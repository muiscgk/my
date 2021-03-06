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
 
 package org.openconcerto.openoffice.spreadsheet;

import org.openconcerto.openoffice.ODDocument;
import org.openconcerto.openoffice.XMLVersion;

import org.jdom.Element;

public class Column<D extends ODDocument> extends TableCalcNode<ColumnStyle, D> {

    static Element createEmpty(XMLVersion ns, ColumnStyle style) {
        final Element res = new Element("table-column", ns.getTABLE());
        if (style != null)
            res.setAttribute("style-name", style.getName(), ns.getTABLE());
        // need default-cell-style-name otherwise some cells types are ignored
        // (eg date treated as a float)
        res.setAttribute("default-cell-style-name", "Default", ns.getTABLE());
        return res;
    }

    public Column(final Table<D> parent, Element tableColElem) {
        super(parent.getODDocument(), tableColElem, ColumnStyle.class);
    }

    public final Float getWidth() {
        final ColumnStyle style = this.getStyle();
        return style == null ? null : style.getWidth();
    }

    public final void setWidth(final Number w) {
        // TODO use Number
        this.getPrivateStyle().setWidth(w.floatValue());
    }
}
