
## edu.gemini.spdb.reports.collection

### Provenance

This bundle originated from `edu.gemini.spdb.reports.collection` in the OCS 1.5 build. It subsumes the following OCS 1.5 bundles.
 
- `edu.gemini.spdb.reports` with **delegate activator** `edu.gemini.spdb.reports.osgi.Activator`
- `edu.gemini.spdb.cron` with **delegate activator** `edu.gemini.spdb.cron.osgi.Activator`
- `edu.gemini.epics.sample.weather.bean` with **delegate activator** `edu.gemini.weather.impl.Activator`
- `edu.gemini.epics.service` with **delegate activator** `edu.gemini.epics.impl.Activator`

The **primary activator** is now `edu.gemini.spdb.cron.osgi.Activator` which delegates to the others.

