/*
 * Copyright 2003 Association for Universities for Research in Astronomy, Inc.,
 * Observatory Control System, Gemini Telescopes Project.
 *
 * $Id: CatalogHistoryList.java 4726 2004-05-14 16:50:12Z brighton $
 */

package jsky.catalog.gui;

import java.util.LinkedList;
import java.util.ListIterator;

import jsky.catalog.CatalogDirectory;
import jsky.util.Preferences;

import java.util.Iterator;

/**
 * Manages a list of catalogs for the history menu
 */
public class CatalogHistoryList {

    // Base filename for serialization of the history list
    private static final String HISTORY_LIST_NAME = "catalogHistoryList";

    // List of CatalogHistoryItem, for previously viewed catalogs or query results.
    private LinkedList<CatalogHistoryItem> _historyList;

    // Max number of items in the history list
    private int _maxHistoryItems = 20;


    /**
     * Constructor
     */
    public CatalogHistoryList() {
        _historyList = new LinkedList<>();
    }


    /**
     * Add the given item to the history stack, removing duplicates.
     */
    @SuppressWarnings("unchecked")
    public void add(CatalogHistoryItem historyItem) {
        // remove duplicates from history list
        final ListIterator<CatalogHistoryItem> it = ((LinkedList<CatalogHistoryItem>) _historyList.clone()).listIterator(0);
        for (int i = 0; it.hasNext(); i++) {
            final CatalogHistoryItem item = it.next();
            if (item.getName().equals(historyItem.getName()))
                _historyList.remove(i);
        }
        _historyList.addFirst(historyItem);
        if (_historyList.size() > _maxHistoryItems)
            _historyList.removeLast();
    }


    /**
     * Return the max number of items in the history list.
     */
    public int getMaxHistoryItems() {
        return _maxHistoryItems;
    }

    /**
     * Set the max number of items in the history list.
     */
    public void setMaxHistoryItems(int n) {
        _maxHistoryItems = n;
    }

    /**
     * Return an iterator over the history list
     */
    public Iterator<CatalogHistoryItem> iterator() {
        return _historyList.iterator();
    }


    /**
     * Make the history list empty
     */
    public void clear() {
        _historyList = new LinkedList<>();
        _save(false);
    }

    // This method is called after the history list is deserialized to remove any
    // items in the list that can't be accessed.
    private void _cleanup() {
        final ListIterator<CatalogHistoryItem> it = _historyList.listIterator(0);
        final CatalogDirectory catDir = CatalogNavigator.getCatalogDirectory();
        while (it.hasNext()) {
            final CatalogHistoryItem item = it.next();
            if (item.getURLStr() == null && catDir.getCatalog(item.getName()) == null)
                it.remove();
        }
    }

    // Try to load the history list from a file, and create an empty list if that fails. */
    @SuppressWarnings("unchecked")
    private void _load() {
        try {
            _historyList = (LinkedList<CatalogHistoryItem>) Preferences.getPreferences().deserialize(HISTORY_LIST_NAME);
            _cleanup();
        } catch (Exception e) {
            _historyList = new LinkedList<>();
        }
    }

    // Merge the _historyList with current serialized version (another instance
    // may have written it since we read it last).
    private LinkedList<CatalogHistoryItem> _merge() {
        LinkedList<CatalogHistoryItem> savedHistory = _historyList;
        _load();

        // Go through the list in reverse, since add() inserts at the start of the list
        ListIterator<CatalogHistoryItem> it = savedHistory.listIterator(savedHistory.size());
        while (it.hasPrevious()) {
            add(it.previous());
        }
        return _historyList;
    }


    // Save the current history list to a file.
    // If merge is true, merge the list with the existing list on disk.
    private void _save(boolean merge) {
        try {
            LinkedList<CatalogHistoryItem> l;
            if (merge)
                l = _merge();
            else
                l = _historyList;
            Preferences.getPreferences().serialize(HISTORY_LIST_NAME, l);
        } catch (Exception e) {
        }
    }
}

