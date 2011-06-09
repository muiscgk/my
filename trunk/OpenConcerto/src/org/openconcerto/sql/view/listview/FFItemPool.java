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
 
 package org.openconcerto.sql.view.listview;

import org.openconcerto.sql.model.SQLField;
import org.openconcerto.sql.model.SQLRowAccessor;
import org.openconcerto.sql.request.SQLForeignRowItemView;
import org.openconcerto.sql.request.SQLRowItemView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

// gère les créés, effacés, inchangés
public abstract class FFItemPool extends ItemPool {

    // [SQLRowItemView]
    // items that have been neither added nor removed since show()
    // but they may have been changed (ie change the CONSTATATION of an observation)
    protected final List stills;
    // items that have been added since show()
    protected final List added;
    // items that have been removed since show()
    protected final List removed;
    // free items, [SQLField]
    protected final List availables;

    public FFItemPool(ItemPoolFactory parent, ListSQLView panel) {
        super(parent, panel);
        this.stills = new ArrayList();
        this.added = new ArrayList();
        this.removed = new ArrayList();
        this.availables = new ArrayList();

        this.reset();
    }

    public final void reset() {
        this.stills.clear();
        this.added.clear();
        this.removed.clear();
        this.availables.clear();
        this.availables.addAll(((FFPoolFactory) this.getCreator()).getFields());
    }

    public final void show(SQLRowAccessor r) {
        this.reset();

        final List availableViews = new ArrayList(this.getPanel().getExistantViews());
        // eg ARTICLE[12], ARTICLE[25]
        final List items = this.getCreator().getItems(r);
        final Iterator iter = items.iterator();
        while (iter.hasNext()) {
            final SQLRowAccessor foreignRow = (SQLRowAccessor) iter.next();
            // reuse existant views and create necessary ones
            final ListItemSQLView v;
            if (availableViews.isEmpty()) {
                v = this.getPanel().addNewItem();
            } else {
                v = (ListItemSQLView) availableViews.remove(0);
                // this.getPanel().readd(v);
                // this.added.add(v.getRowItemView());
            }
            ((SQLForeignRowItemView) v.getRowItemView()).setValue(foreignRow);
        }

        this.stills.clear();
        this.stills.addAll(this.added);
        this.added.clear();
    }

    public final boolean availableItem() {
        return this.availables.size() > 0;
    }

    public abstract SQLRowItemView getNewItem() throws IllegalStateException;

    protected final String getLabel(SQLField field) {
        return field.getName();
    }

    public final void removeItem(SQLRowItemView v) {
        // it could be in either one
        this.stills.remove(v);
        this.added.remove(v);

        // do not add it to available, you must commit before
        this.removed.add(v);
        this.itemRemoved(v);
    }

    protected void itemRemoved(SQLRowItemView v) {
        // don't do anything by default
    }

    //

    public List getItems() {
        final List res = new ArrayList(this.stills);
        res.addAll(this.added);
        return res;
    }

    public List getAddedItems() {
        return Collections.unmodifiableList(this.added);
    }

    public List getRemovedItems() {
        return Collections.unmodifiableList(this.removed);
    }

    public String toString() {
        String res = "Pool on " + this.getCreator();
        res += "\nstills: " + this.stills;
        res += "\nadded: " + this.added;
        res += "\nremoved: " + this.removed;
        res += "\navailables: " + this.availables;
        return res;
    }

}
