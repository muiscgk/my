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
 
 package org.openconcerto.erp.core.finance.accounting.element;

import org.openconcerto.erp.core.common.element.ComptaSQLConfElement;
import org.openconcerto.sql.element.BaseSQLComponent;
import org.openconcerto.sql.element.SQLComponent;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JTextField;

/***************************************************************************************************
 * Une pièce regroupe l'ensemble d'une opération comptable c'est un ensemble de mouvement
 **************************************************************************************************/
public class PieceSQLElement extends ComptaSQLConfElement {

    private static final String NOM = "NOM";

    public PieceSQLElement() {
        super("PIECE", "une pièce comptable", "pièces comptables");
    }

    protected List<String> getComboFields() {
        final List<String> list = new ArrayList<String>(1);
        list.add(NOM);
        return list;
    }

    protected List<String> getListFields() {
        final List<String> list = new ArrayList<String>(1);
        list.add(NOM);
        return list;
    }

    public SQLComponent createComponent() {
        return new BaseSQLComponent(this) {
            public void addViews() {
                final JLabel labelNom = new JLabel(getLabelFor(NOM));
                this.add(labelNom);
                final JTextField piece = new JTextField();
                this.add(piece);
                this.addSQLObject(piece, NOM);
            }
        };
    }
}
