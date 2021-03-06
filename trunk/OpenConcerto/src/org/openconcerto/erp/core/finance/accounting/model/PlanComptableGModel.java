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
 
 package org.openconcerto.erp.core.finance.accounting.model;

import org.openconcerto.erp.config.ComptaPropsConfiguration;
import org.openconcerto.erp.element.objet.ClasseCompte;
import org.openconcerto.erp.element.objet.Compte;
import org.openconcerto.sql.Configuration;
import org.openconcerto.sql.model.SQLBase;
import org.openconcerto.sql.model.SQLSelect;
import org.openconcerto.sql.model.SQLSystem;
import org.openconcerto.sql.model.SQLTable;
import org.openconcerto.sql.model.Where;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

import org.apache.commons.dbutils.handlers.ArrayListHandler;

public class PlanComptableGModel extends AbstractTableModel {

    private Vector<String> titres = new Vector<String>();
    private Vector<Compte> comptes = new Vector<Compte>();

    // Compte ID - Vecteur compte index
    private Map<Integer, Integer> mapCompte = new HashMap<Integer, Integer>();

    /**
     * Permet d'afficher le plan comptable général d'une classe
     * 
     * @param classeDuCompte classe de compte à afficher
     * @param typePlan type de plan 1 : base, 2 : abrégé, 3 : developpé (0: plan comptable
     *        entreprise)
     */
    public PlanComptableGModel(ClasseCompte classeDuCompte, int typePlan) {
        this(classeDuCompte, typePlan, "COMPTE_PCG");
    }

    protected PlanComptableGModel(ClasseCompte classeDuCompte, String table) {
        // type de plan 0 pour plan comptable entreprise
        this(classeDuCompte, 0, table);
    }

    private PlanComptableGModel(final ClasseCompte classeDuCompte, final int typePlan, final String table) {

        new SwingWorker<String, Object>() {

            @Override
            protected String doInBackground() throws Exception {
                // on recupere les comptes
                final SQLBase base = ((ComptaPropsConfiguration) Configuration.getInstance()).getSQLBaseSociete();
                final SQLTable compteTable = base.getTable(table);
                final SQLSelect selCompte = new SQLSelect(base);
                selCompte.addSelect(compteTable.getField("ID"));
                selCompte.addSelect(compteTable.getField("NUMERO"));
                selCompte.addSelect(compteTable.getField("NOM"));
                selCompte.addSelect(compteTable.getField("INFOS"));

                String function = "REGEXP";
                String match = classeDuCompte.getTypeNumeroCompte();
                if (Configuration.getInstance().getBase().getServer().getSQLSystem() == SQLSystem.POSTGRESQL) {
                    function = "~";
                }

                Where w1 = new Where(compteTable.getField("NUMERO"), function, match);
                final Where w2;
                if (typePlan == 1) {
                    w2 = new Where(compteTable.getField("ID_TYPE_COMPTE_PCG_BASE"), "!=", 1);
                } else if (typePlan == 2) {
                    w2 = new Where(compteTable.getField("ID_TYPE_COMPTE_PCG_AB"), "!=", 1);
                } else if (typePlan == 3 || typePlan == 0) {
                    w2 = null;
                } else {
                    throw new IllegalArgumentException("Type de PCG inconnu : " + typePlan);
                }
                if (w2 != null) {
                    w1 = w1.and(w2);
                }
                selCompte.setWhere(w1);

                selCompte.addRawOrder("\"" + table + "\".\"NUMERO\"");
                // selCompte.setDistinct(true);

                String reqCompte = selCompte.asString();

                Object obCompte = base.getDataSource().execute(reqCompte, new ArrayListHandler());

                List myListCompte = (List) obCompte;

                if (myListCompte != null) {
                    int size = myListCompte.size();
                    for (int i = 0; i < size; i++) {
                        Object[] objTmp = (Object[]) myListCompte.get(i);

                        final Integer numero = new Integer(Integer.parseInt(objTmp[0].toString()));
                        PlanComptableGModel.this.mapCompte.put(numero, new Integer(PlanComptableGModel.this.comptes.size()));
                        PlanComptableGModel.this.comptes.add(new Compte(numero, objTmp[1].toString(), objTmp[2].toString(), objTmp[3].toString()));
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                PlanComptableGModel.this.fireTableDataChanged();
            }
        }.execute();

        this.titres.add("Compte");
        this.titres.add("Libellé");
    }

    public Class getColumnClass(int c) {
        return String.class;
    }

    public int getRowCount() {
        return this.comptes.size();
    }

    public int getColumnCount() {
        return this.titres.size();
    }

    public String getColumnName(int col) {
        return this.titres.get(col).toString();
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
            return this.comptes.get(rowIndex).getNumero();
        } else if (columnIndex == 1) {
            return this.comptes.get(rowIndex).getNom();
        }
        return null;
    }

    public int getId(int row) {
        return this.comptes.get(row).getId();
    }

    public Map<Integer, Integer> getMapComptes() {
        return this.mapCompte;
    }

    public Vector<Compte> getComptes() {
        return this.comptes;
    }

    public String getInfosAt(int row) {
        return this.comptes.get(row).getInfos();
    }

}
