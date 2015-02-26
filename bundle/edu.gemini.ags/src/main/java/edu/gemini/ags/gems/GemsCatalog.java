package edu.gemini.ags.gems;

import edu.gemini.catalog.api.FaintnessConstraint;
import edu.gemini.catalog.api.MagnitudeConstraints;
import edu.gemini.catalog.api.RadiusConstraint;
import edu.gemini.catalog.api.SaturationConstraint;
import edu.gemini.catalog.skycat.table.CatalogHeader;
import edu.gemini.catalog.skycat.table.CatalogRow;
import edu.gemini.catalog.skycat.table.DefaultCatalogHeader;
import edu.gemini.catalog.skycat.table.DefaultCatalogRow;
import edu.gemini.catalog.skycat.table.SkyObjectFactory;
import edu.gemini.shared.skyobject.SkyObject;
import edu.gemini.shared.skyobject.coords.SkyCoordinates;
import edu.gemini.shared.util.immutable.*;
import edu.gemini.spModel.core.Coordinates;
import edu.gemini.spModel.core.Magnitude;
import edu.gemini.spModel.core.MagnitudeBand;
import edu.gemini.spModel.core.Target;
import edu.gemini.spModel.gemini.gems.GemsInstrument;
import edu.gemini.spModel.obs.context.ObsContext;
import jsky.catalog.BasicQueryArgs;
import jsky.catalog.Catalog;
import jsky.catalog.FieldDesc;
import jsky.catalog.QueryArgs;
import jsky.catalog.QueryResult;
import jsky.catalog.TableQueryResult;
import jsky.catalog.ValueRange;
import jsky.catalog.skycat.SkyObjectFactoryRegistrar;
import jsky.catalog.skycat.SkycatConfigFile;
import jsky.catalog.skycat.SkycatTable;
import jsky.catalog.skycat.UserCatalogSkyObjectFactory;
import jsky.coords.CoordinateRadius;
import jsky.coords.WorldCoords;
import jsky.util.gui.StatusLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements GeMS guide star search.
 * The catalog search will provide the inputs to the analysis phase, which actually assigns guide stars to guiders.
 * See OT-26
 */
public class GemsCatalog {

    private static final Logger LOG = Logger.getLogger(GemsCatalog.class.getName());

    // Default, If no magnitude saturation limit is provided
    private static final double DEFAULT_SATURATION_MAGNITUDE = 0.0;

    // Listener for results of a catalog query from a background thread
    private interface SearchResultsListener {
        void setResults(List<GemsCatalogSearchResults> results);
        void setException(Exception e);
    }

    /**
     * Searches for the given base position according to the given options.
     * Multiple queries are performed in parallel in background threads.
     *
     * @param obsContext   the context of the observation (needed to adjust for selected conditions)
     * @param basePosition the base position to search for
     * @param options      the search options
     * @param nirBand      optional NIR magnitude band (default is H)
     * @return list of search results
     */
    public List<GemsCatalogSearchResults> search(final ObsContext obsContext, final SkyCoordinates basePosition, final GemsGuideStarSearchOptions options,
                                                 final Option<MagnitudeBand> nirBand, final StatusLogger statusLogger)
            throws Exception {
        final Map<GemsCatalogSearchCriterion, List<Target.SiderealTarget>> map = new HashMap<>();
        final List<Exception> exceptions = new ArrayList<>();

        final SearchResultsListener searchResultsListener = new SearchResultsListener() {
            @Override
            public void setResults(List<GemsCatalogSearchResults> results) {
                mapResults(map, results);
            }

            @Override
            public void setException(Exception e) {
                exceptions.add(e);
            }
        };

        final List<GemsCatalogSearchCriterion> criterList = options.searchCriteria(obsContext, nirBand);
        final Set<String> catalogs = options.getCatalogs();
        final GemsInstrument inst = options.getInstrument();
        if (inst == GemsInstrument.flamingos2) {
            // Don't optimize search for flamingos, since difference in size between oiwfs and canopus is too large
            wait(search(basePosition, criterList, catalogs, searchResultsListener, statusLogger));
        } else {
            wait(searchOptimized(basePosition, criterList, catalogs, inst, searchResultsListener, statusLogger));
        }
        if (!exceptions.isEmpty()) throw exceptions.get(0);
        return mergeResults(map, criterList);
    }

    // Returns when all of the threads have completed
    private void wait(List<Thread> threadList) {
        for(Thread thread : threadList) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Searches the given catalogs for the given base position according to the given criteria.
     * This method runs the catalog searches in parallel in background threads and notifies the
     * searchResultsListener when done.
     *
     * @param basePosition the base position to search for
     * @param criterList list of search criteria
     * @param catalogs the set of catalogs to search
     * @param searchResultsListener notified when search results are available
     * @return a list of threads used for background catalog searches
     */
    private List<Thread> search(final SkyCoordinates basePosition, final List<GemsCatalogSearchCriterion> criterList,
                                final Set<String> catalogs, final SearchResultsListener searchResultsListener,
                                final StatusLogger statusLogger) {

        final List<Thread> threadList = new ArrayList<>();
        final List<Tuple2<Catalog, SkyObjectFactory>> skyObjectFactoryList = getSkyObjectFactoryList(catalogs, criterList);
        if (!skyObjectFactoryList.isEmpty()) {
            for (GemsCatalogSearchCriterion criter : criterList) {
                for (Tuple2<Catalog, SkyObjectFactory> t : skyObjectFactoryList) {
                    final CatalogSearchCriterion c = criter.criterion();
                    threadList.add(searchCatalog(t._1(), t._2(), basePosition, criterList, c.radiusLimits(),
                            c.magLimits(), searchResultsListener, statusLogger));
                }
            }
        }

        return threadList;
    }




    /**
     * Searches the given catalogs for the given base position according to the given criteria.
     * This method attempts to merge the criteria to avoid multiple catalog queries and then
     * runs the catalog searches in parallel in background threads and notifies the
     * searchResultsListener when done.
     *
     * @param basePosition the base position to search for
     * @param criterList list of search criteria
     * @param catalogs the set of catalogs to search
     * @param inst the instrument option for the search
     * @param searchResultsListener notified when search results are available
     * @return a list of threads used for background catalog searches
     */
    private List<Thread> searchOptimized(final SkyCoordinates basePosition, final List<GemsCatalogSearchCriterion> criterList,
                                         final Set<String> catalogs, final GemsInstrument inst, final SearchResultsListener searchResultsListener,
                                         final StatusLogger statusLogger) {

        final List<RadiusConstraint> radiusLimitsList = getRadiusLimits(inst, criterList);
        final List<MagnitudeConstraints> magLimitsList = optimizeMagnitudeLimits(criterList);
        final List<Tuple2<Catalog, SkyObjectFactory>> skyObjectFactoryList = getSkyObjectFactoryList(catalogs, criterList);
        final List<Thread> threadList = new ArrayList<>();
        if (!skyObjectFactoryList.isEmpty()) {
            // Some search criteria will be identical and some will be overlapping (with different
            // min/max radius or magnitude). To the extent possible, minimize the number of remote
            // calls to the catalogs. When using multiple catalogs or multiple calls to the same catalog
            // with different parameters, perform the lookup in parallel.
            //
            // The CatalogSearchCriterion can be used to filter output from the catalogs if multiple
            // criterion are combined in a single remote call to a catalog. In the results, we want each
            // GemsCatalogSearchResults to contain only SkyObjects that match the criterion.

            // Note: We need to do a separate catalog search for each set of mag limits, since including them in one search
            // would be ANDing them together, which would normally result in an empty list. Use the map to merge
            // query results from multiple catalog queries based on the original criterion.
            for (RadiusConstraint radiusLimits : radiusLimitsList) {
                for (MagnitudeConstraints magLimits : magLimitsList) {
                    // Multiple queries: perform the lookup in parallel in background threads
                    for (Tuple2<Catalog, SkyObjectFactory> t : skyObjectFactoryList) {
                        threadList.add(searchCatalog(t._1(), t._2(), basePosition, criterList, radiusLimits, magLimits,
                                searchResultsListener, statusLogger));
                    }
                }
            }
        }
        return threadList;
    }


    // Store the given results in the given map. This is used to later merge the results.
    private synchronized void mapResults(final Map<GemsCatalogSearchCriterion, List<Target.SiderealTarget>> map, final List<GemsCatalogSearchResults> results) {
        for(GemsCatalogSearchResults result : results) {
            final GemsCatalogSearchCriterion criter = result.criterion();
            if (map.containsKey(criter)) {
                map.get(criter).addAll(result.resultsAsJava());
            } else {
                map.put(criter, result.resultsAsJava());
            }
        }
    }

    // Returns a list of results, merged based on the criterion, in the original order
    private List<GemsCatalogSearchResults> mergeResults(final Map<GemsCatalogSearchCriterion, List<Target.SiderealTarget>> map,
                                                        final List<GemsCatalogSearchCriterion> criterList) {
        final List<GemsCatalogSearchResults> results = new ArrayList<>();
        for(GemsCatalogSearchCriterion criter : criterList) {
            if (map.containsKey(criter)) {
                results.add(new GemsCatalogSearchResults(map.get(criter), criter));
            }
        }
        return results;
    }


    // Performs a search in the given catalog in a background thread and calls the given listener with the results.
    // Returns the thread object.
    private Thread searchCatalog(final Catalog catalog, final SkyObjectFactory factory, final SkyCoordinates basePosition,
                                                  final List<GemsCatalogSearchCriterion> criterList,
                                                  final RadiusConstraint radiusLimits, final MagnitudeConstraints magLimits,
                                                  final SearchResultsListener searchResultsListener,
                                                  final StatusLogger statusLogger) {
        final Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final Coordinates coordinates = GemsUtils4Java.toCoordinates(basePosition);
                try {
                    final QueryArgs queryArgs = getQueryArgs(catalog, factory, coordinates, radiusLimits, magLimits, statusLogger);
                    final QueryResult queryResult = catalog.query(queryArgs);
                    final TableQueryResult table = (TableQueryResult) queryResult;
                    searchResultsListener.setResults(filter(coordinates, table, factory, criterList));
                } catch (Exception e) {
                    searchResultsListener.setException(e);
                }
            }
        });
        t.start();
        return t;
    }


    // Filters the table query results according to the given criteria and packs the results in the return list.
    private List<GemsCatalogSearchResults> filter(final Coordinates basePosition, final TableQueryResult table,
                                                  final SkyObjectFactory factory, final List<GemsCatalogSearchCriterion> criterList) {
        final List<GemsCatalogSearchResults> searchResultsList = new ArrayList<>();
        for (GemsCatalogSearchCriterion criter : criterList) {
            searchResultsList.add(new GemsCatalogSearchResults(criter, match(basePosition, table, factory, criter)));
        }
        return searchResultsList;
    }


    // Returns a list of matching sky objects based on the given criterion
    private List<SkyObject> match(final Coordinates basePosition, final TableQueryResult table,
                                  final SkyObjectFactory factory, final GemsCatalogSearchCriterion criter ) {
        final int numRows = table.getRowCount();
        final Vector<Vector<Object>> dataVector = table.getDataVector();
        final CatalogSearchCriterion.Matcher matcher = criter.criterion().matcher(basePosition);
        final List<SkyObject> skyObjectList = new ArrayList<>();
        for (int i = 0; i < numRows; i++) {
            final Option<SkyObject> skyObjectOpt = toSkyObject(table, factory, dataVector.get(i));
            if (!skyObjectOpt.isEmpty()) {
                final SkyObject skyObject = skyObjectOpt.getValue();
                if (matcher.matches(GemsUtils4Java.toSiderealTarget(skyObject))) {
                    skyObjectList.add(skyObject);
                }
            }
        }
        return skyObjectList;
    }

    // Returns the query arguments to use to query the given catalog with the given base position,
    // radius and magnitude limits.
    private QueryArgs getQueryArgs(final Catalog catalog, final SkyObjectFactory factory, final Coordinates basePosition,
                                   final RadiusConstraint radiusLimits, final MagnitudeConstraints magLimits, final StatusLogger statusLogger) {
        final BasicQueryArgs queryArgs = new BasicQueryArgs(catalog);
        queryArgs.setRegion(getRegion(basePosition, radiusLimits));
        setMagnitudeQueryArgs(queryArgs, catalog, factory, magLimits);
        queryArgs.setMaxRows(1000);
        queryArgs.setStatusLogger(statusLogger);
        return queryArgs;
    }

    // Sets the min/max magnitude limits in the given query arguments
    private void setMagnitudeQueryArgs(final BasicQueryArgs queryArgs, final Catalog catalog, final SkyObjectFactory factory,
                                       final MagnitudeConstraints magLimits) {
        final int numParams = catalog.getNumParams();
        final String magColumn = factory.getMagColumn(GemsUtils4Java.toOldBand(magLimits.band()));
        for (int i = 0; i < numParams; i++) {
            final FieldDesc fieldDesc = catalog.getParamDesc(i);
            if (magColumn.equals(fieldDesc.getId()) || magColumn.equals(fieldDesc.getName())) {
                final Double maxMag = magLimits.faintnessConstraint().brightness();
                Double minMag = null;
                final Option<Double> limit = GemsUtils4Java.mapMagnitudes(magLimits.saturationConstraint(), new MapOp<SaturationConstraint, Double>() {
                    public Double apply(SaturationConstraint sl) {
                        return sl.brightness();
                    }
                });
                if (!limit.isEmpty()) {
                    minMag = limit.getValue();
                }
                if (fieldDesc.isMax()) {
                    queryArgs.setParamValue(i, maxMag);
                } else if (fieldDesc.isMin()) {
                    if (minMag != null) {
                        queryArgs.setParamValue(i, minMag);
                    }
                } else {
                    // for local catalogs use a value range object, since there are no min/max params defined
                    // (For skycat catalogs, these are defined in the skycat.cfg file)
                    if (minMag != null) {
                        queryArgs.setParamValue(i, new ValueRange(minMag, maxMag));
                    } else {
                        queryArgs.setParamValue(i, new ValueRange(maxMag));
                    }
                }
            }
        }
    }

    // Returns the query region to use based on the given base position and radius limits
    private CoordinateRadius getRegion(final Coordinates basePosition, final RadiusConstraint radiusLimits) {
        // Equinox is 2000 by default
        final WorldCoords pos = new WorldCoords(
                basePosition.ra().toAngle().toDegrees(),
                basePosition.dec().toDegrees());
        return new CoordinateRadius(pos,
                radiusLimits.maxLimit().toArcmins(),
                radiusLimits.minLimit().toArcmins());
    }

    // Returns a list of radius limits used in the criteria.
    // If inst is flamingos2, use separate limits, since the difference in size between the OIWFS and Canopus
    // areas is too large to get good results.
    // Otherwise, for GSAOI, merge the radius limits into one, since the Canopus and GSAOI radius are both about
    // 1 arcmin.
    private List<RadiusConstraint> getRadiusLimits(final GemsInstrument inst,final  List<GemsCatalogSearchCriterion> criterList) {
        final List<RadiusConstraint> result = new ArrayList<>(criterList.size());
        if (inst == GemsInstrument.flamingos2) {
            for (GemsCatalogSearchCriterion criter : criterList) {
                result.add(criter.criterion().adjustedLimits());
            }
        } else {
            result.add(GemsUtils4Java.optimizeRadiusConstraint(criterList));
        }
        return result;
    }

    // Combines multiple magnitude limits into one (using OR)
    private List<MagnitudeConstraints> optimizeMagnitudeLimits(final List<GemsCatalogSearchCriterion> criterList) {
        final Map<MagnitudeBand, Double> faintMap = new HashMap<>();
        final Map<MagnitudeBand, Double> saturationMap = new HashMap<>();
        for (GemsCatalogSearchCriterion criter : criterList) {
            final CatalogSearchCriterion c = criter.criterion();
            final MagnitudeConstraints magLimits = c.magLimits();
            final MagnitudeBand band = magLimits.band();
            final Double faintLimit = magLimits.faintnessConstraint().brightness();
            if (!faintMap.containsKey(band) || faintLimit > faintMap.get(band)) {
                faintMap.put(band, faintLimit);
            }
            final Double saturationLimit = GemsUtils4Java.mapMagnitudes(magLimits.saturationConstraint(), new MapOp<SaturationConstraint, Double>() {
                public Double apply(SaturationConstraint sl) {
                    return sl.brightness();
                }
            }).getOrElse(DEFAULT_SATURATION_MAGNITUDE);
            if (!saturationMap.containsKey(band) || saturationLimit < saturationMap.get(band)) {
                saturationMap.put(band, saturationLimit);
            }
        }
        final List<MagnitudeConstraints> result = new ArrayList<>(faintMap.size());
        for (MagnitudeBand band : faintMap.keySet()) {
            if (saturationMap.containsKey(band)) {
                result.add(new MagnitudeConstraints(band, new FaintnessConstraint(faintMap.get(band)), new scala.Some<>(new SaturationConstraint(saturationMap.get(band)))));
            } else {
                result.add(new MagnitudeConstraints(new Magnitude(faintMap.get(band), band)));
            }
        }
        return result;
    }

    // For each catalog in the catalogs set, find the corresponding SkyObjectFactory
    // by looking it up in SkyObjectFactoryRegistrar. Use the SkyObjectFactory.bands method
    // to determine whether the catalogs will provide SkyObjects with the required bandpass
    // to match the criteria. Returns a list of (Catalog, SkyObjectFactory) pairs with
    // matching bandwidth support.
    private List<Tuple2<Catalog, SkyObjectFactory>> getSkyObjectFactoryList(final Set<String> catalogs,
                                                                            final List<GemsCatalogSearchCriterion> criterList) {
        final List<Tuple2<Catalog, SkyObjectFactory>> skyObjectFactoryList = new ArrayList<>();
        for (String name : catalogs) {
            Catalog catalog = SkycatConfigFile.getConfigFile().getCatalog(name);
            if (catalog == null) {
                // REL-560: User catalog support: name may be a path name of a local file containing the data in
                // skycat catalog format
                if (new File(name).exists()) {
                    try {
                        catalog = new SkycatTable(name);
                    } catch(IOException ignored) {
                        catalog = null;
                    }
                }
            }
            if (catalog == null) {
                throw new RuntimeException("Catalog '"
                        + name
                        + "' not found in "
                        + SkycatConfigFile.getConfigFile().getURL());
            }
            Option<SkyObjectFactory> factoryOpt;
            if (catalog instanceof TableQueryResult) {
                factoryOpt = new Some<SkyObjectFactory>(new UserCatalogSkyObjectFactory((TableQueryResult)catalog));
            } else {
                factoryOpt = SkyObjectFactoryRegistrar.instance.lookup(name);
            }
            if (!factoryOpt.isEmpty()) {
                final SkyObjectFactory fact = factoryOpt.getValue();
                final Set<MagnitudeBand> catalogBands = GemsUtils4Java.translateBands(fact.bands());
                assertHasRequiredBands(name, criterList, catalogBands);
                skyObjectFactoryList.add(new Pair<>(catalog, fact));
            }
        }
        return skyObjectFactoryList;
    }

    // Makes sure the given set of bands contains all of the bands used in the given criteria
    private void assertHasRequiredBands(final String name, final List<GemsCatalogSearchCriterion> criterList, final Set<MagnitudeBand> bands) {
        for (GemsCatalogSearchCriterion criter : criterList) {
            final MagnitudeBand band = criter.criterion().magLimits().band();
            if (!bands.contains(band)) {
                throw new RuntimeException("Catalog '"
                        + new File(name).getName()
                        + "' does not contain the required magnitude band '" + band + "'.");
            }
        }
    }

    // Creates a CatalogHeader and CatalogRow from the corresponding Collections
    @SuppressWarnings("varargs")
    private static Tuple2<CatalogHeader, CatalogRow> wrap(final Collection<String> header, final Collection<Object> row) {
        ImList<Tuple2<String,Class<?>>> colLst = DefaultImList.create();
        for (String col : header) {
            colLst = colLst.append(new Pair<String,Class<?>>(col, String.class));
        }
        return new Pair<CatalogHeader,  CatalogRow>(
                new DefaultCatalogHeader(colLst),
                new DefaultCatalogRow(DefaultImList.create(row)));
    }

    // Creates a SkyObject, if possible, using the given table query result
    // and row of data.
    private static Option<SkyObject> toSkyObject(final TableQueryResult table, final SkyObjectFactory factory, final Vector<Object> row) {
        final Vector<String> columnIdentifiers = table.getColumnIdentifiers();
        final Tuple2<CatalogHeader, CatalogRow> cat = wrap(columnIdentifiers, row);
        try {
            return new Some<>(factory.create(cat._1(), cat._2()));
        } catch (edu.gemini.catalog.skycat.CatalogException ex) {
            LOG.log(Level.WARNING, ex.getMessage(), ex);
        }
        return None.instance();
    }
}
