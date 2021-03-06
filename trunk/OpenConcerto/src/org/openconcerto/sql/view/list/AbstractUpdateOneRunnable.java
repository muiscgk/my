/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2011 OpenConcerto, by ILM Informatique. All rights reserved.
 * 
 * The contents of this file are subject to the terms of the GNU General Public License Version 3
 * only ("GPL"). You may not use this file except in compliance with the License. You can obtain a
 * copy of the License at http://www.gnu.org/licenses/gpl-3.0.html See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each file.
 */
 
 package org.openconcerto.sql.view.list;

import org.openconcerto.sql.Log;
import org.openconcerto.sql.model.SQLRow;
import org.openconcerto.sql.model.SQLRowValues;
import org.openconcerto.sql.model.SQLRowValues.CreateMode;
import org.openconcerto.sql.model.SQLRowValuesListFetcher;
import org.openconcerto.sql.model.SQLSelect;
import org.openconcerto.sql.model.Where;
import org.openconcerto.sql.model.graph.Path;
import org.openconcerto.sql.request.BaseFillSQLRequest;
import org.openconcerto.sql.request.ListSQLRequest;
import org.openconcerto.sql.view.list.search.SearchQueue;
import org.openconcerto.utils.CollectionUtils;
import org.openconcerto.utils.ListMap;
import org.openconcerto.utils.cc.ITransformer;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

abstract class AbstractUpdateOneRunnable extends UpdateRunnable {

    public AbstractUpdateOneRunnable(ITableModel model, final SQLRow r) {
        super(model, r);
        if (this.getID() < SQLRow.MIN_VALID_ID)
            throw new IllegalArgumentException("id is not valid : " + this.getID());
    }

    protected final ListMap<Path, ListSQLLine> getAffectedPaths() {
        return this.getSearchQ().getAffectedPaths(this.getRow());
    }

    protected final void updateLines(ListMap<Path, ListSQLLine> paths) {
        for (final Entry<Path, ? extends Collection<ListSQLLine>> e : paths.entrySet()) {
            // eg SITE.ID_CONTACT_CHEF
            final Path p = e.getKey();
            // eg [SQLRowValues(SITE), SQLRowValues(SITE)]
            final List<ListSQLLine> lines = (List<ListSQLLine>) e.getValue();
            if (!lines.isEmpty()) {
                // deepCopy() instead of new SQLRowValues() otherwise the used line's graph will be
                // modified (eg the new instance would be linked to it)
                final SQLRowValues proto = getModel().getLinesSource().getParent().getMaxGraph().followPathToOne(p, CreateMode.CREATE_NONE, false).deepCopy();
                final String lastReferentField = SearchQueue.getLastReferentField(p);
                // there's only one path from the graph start to proto, and we will graft the newly
                // fetched values at the end of p, so remove other values
                if (lastReferentField != null) {
                    proto.put(lastReferentField, null);
                } else {
                    proto.clearReferents();
                    // keep only what has changed, eg CONTACT.NOM
                    proto.retainAll(getModifedFields());
                }
                // the modified fields aren't used at the path (e.g. if we display a row and its
                // same-table origin, the event was added by UpdateQueue.rowModified() since the
                // the modified fields are displayed for the primary row, but might not for the
                // origin)
                if (!proto.getFields().isEmpty()) {
                    // fetch the changed rowValues
                    // ATTN this doesn't use the original fetcher that was used in the updateAll
                    // MAYBE add a slower but accurate mode using the updateAll fetcher (and thus
                    // reloading rows from the primary table and not just the changed rows)
                    final SQLRowValuesListFetcher fetcher = SQLRowValuesListFetcher.create(proto);
                    BaseFillSQLRequest.setupForeign(fetcher);
                    final ITransformer<SQLSelect, SQLSelect> transf = new ITransformer<SQLSelect, SQLSelect>() {
                        @Override
                        public SQLSelect transformChecked(SQLSelect input) {
                            if (ListSQLRequest.getDefaultLockSelect())
                                input.addWaitPreviousWriteTXTable(getTable().getName());
                            return input.setWhere(new Where(getTable().getKey(), "=", getID()));
                        }
                    };
                    fetcher.setSelTransf(transf);
                    final List<SQLRowValues> fetched = fetcher.fetch();
                    if (fetched.size() > 1)
                        throw new IllegalStateException("more than one row fetched for " + this + " with " + fetcher.getReq() + " :\n" + fetched);

                    // OK if lastReferentField != null : a referent row has been deleted
                    if (fetched.size() == 0 && lastReferentField == null) {
                        Log.get().fine("no row fetched for " + this + ", lines have been changed without the TableModel knowing : " + lines + " req :\n" + fetcher.getReq());
                        getModel().updateAll();
                    } else {
                        final SQLRowValues soleFetched = CollectionUtils.getSole(fetched);
                        // copy it to each affected lines
                        for (final ListSQLLine line : lines) {
                            line.loadAt(getID(), soleFetched, p);
                        }
                    }
                }
            }
        }
    }

    protected abstract Collection<String> getModifedFields();
}
