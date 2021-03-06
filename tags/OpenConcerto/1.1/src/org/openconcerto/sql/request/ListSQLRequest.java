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
 
 package org.openconcerto.sql.request;

import org.openconcerto.sql.Configuration;
import org.openconcerto.sql.ShowAs;
import org.openconcerto.sql.model.SQLField;
import org.openconcerto.sql.model.SQLRowValues;
import org.openconcerto.sql.model.SQLTable;
import org.openconcerto.sql.model.Where;
import org.openconcerto.sql.model.SQLRowValuesCluster.State;
import org.openconcerto.utils.cc.ITransformer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ListSQLRequest extends FilteredFillSQLRequest {

    /**
     * Copy the passed request and replace its where.
     * 
     * @param src the object to copy.
     * @param newWhere the where of the copy.
     * @return a copy of <code>src</code>.
     */
    public static final ListSQLRequest copy(ListSQLRequest src, final Where newWhere) {
        final ListSQLRequest res = new ListSQLRequest(src);
        res.setWhere(newWhere);
        return res;
    }

    // les champs à afficher (avant expansion)
    private final List<SQLField> listFields;

    public ListSQLRequest(SQLTable table, List fieldss) {
        this(table, fieldss, null);
    }

    // fieldss : list de String (préferré) ou de SQLField
    public ListSQLRequest(SQLTable table, List fieldss, Where where) {
        this(table, fieldss, where, null);
    }

    public ListSQLRequest(SQLTable table, List fieldss, Where where, final SQLTable cutAtAncestor) {
        super(table, where);
        if (!this.getPrimaryTable().isOrdered())
            throw new IllegalArgumentException(table + " is not ordered.");
        if (cutAtAncestor == table)
            throw new IllegalArgumentException("the primaryTable: " + this.getPrimaryTable() + "is the same than cutAtAncestor");

        this.listFields = new ArrayList<SQLField>();
        for (final Object field : fieldss) {
            final SQLField f;
            if (field instanceof String)
                f = this.getPrimaryTable().getField((String) field);
            else if (field instanceof SQLField) {
                final SQLField fToCheck = (SQLField) field;
                if (fToCheck.getTable().equals(this.getPrimaryTable()))
                    f = fToCheck;
                else
                    throw new IllegalArgumentException("field " + fToCheck + " not part of the primary table : " + this.getPrimaryTable());
            } else
                throw new IllegalArgumentException("must be a fieldname or a SQLField but got : " + field);

            this.listFields.add(f);
        }

        if (cutAtAncestor != null)
            this.getGraph().walkGraph(null, new ITransformer<State<Object>, Object>() {
                @Override
                public Object transformChecked(State<Object> input) {
                    final SQLRowValues current = input.getCurrent();
                    for (final String field : new HashSet<String>(current.getFields())) {
                        final Object value = current.getObject(field);
                        if (value instanceof SQLRowValues && ((SQLRowValues) value).getTable() == cutAtAncestor)
                            current.remove(field);
                    }
                    return null;
                }
            });
    }

    public ListSQLRequest(ListSQLRequest req) {
        super(req);
        this.listFields = new ArrayList<SQLField>(req.listFields);
    }

    protected final ShowAs getShowAs() {
        return Configuration.getInstance().getShowAs();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.openconcerto.devis.request.BaseSQLRequest#getFields()
     */
    public final List<SQLField> getFields() {
        return this.listFields;
    }

    @Override
    protected void customizeToFetch(SQLRowValues graphToFetch) {
        super.customizeToFetch(graphToFetch);
        addField(graphToFetch, getPrimaryTable().getCreationDateField());
        addField(graphToFetch, getPrimaryTable().getCreationUserField());
        addField(graphToFetch, getPrimaryTable().getModifDateField());
        addField(graphToFetch, getPrimaryTable().getModifUserField());
    }

    private void addField(SQLRowValues graphToFetch, final SQLField f) {
        if (f != null)
            if (f.isKey())
                graphToFetch.put(f.getName(), new SQLRowValues(f.getTable().getForeignTable(f.getName())).put("NOM", null).put("PRENOM", null));
            else
                graphToFetch.put(f.getName(), null);
    }
}
