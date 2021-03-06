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
 
 package org.openconcerto.sql.model;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

public class SQLRequestLogModel extends DefaultTableModel {

    SQLRequestLogModel() {
        SQLRequestLog.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                fireTableDataChanged();
            }
        });
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex < 4)
            return Long.class;
        return String.class;
    }

    @Override
    public int getColumnCount() {
        return 8;
    }

    @Override
    public String getColumnName(int columnIndex) {
        switch (columnIndex) {
        case 0:
            return "Heure";
        case 1:
            return "Durée totale";
        case 2:
            return "Durée SQL";
        case 3:
            return "Traitement";
        case 4:
            return "Requête";
        case 5:
            return "Infos";
        case 6:
            return "Connexion";
        case 7:
            return "Thread";
        }
        return "??";
    }

    @Override
    public int getRowCount() {
        return SQLRequestLog.getSize();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        final SQLRequestLog l = getRowAt(rowIndex);
        switch (columnIndex) {
        case 0:
            return l.getStartAsMs();
        case 1:
            return l.getDurationTotalNano();
        case 2:
            return l.getDurationSQLNano();
        case 3:
            return l.getDurationTotalNano() - l.getDurationSQLNano();
        case 4:
            return l.getQuery();
        case 5:
            return l.getConnection();
        case 6:
            if (l.getConnectionId() == 0)
                return "";
            return String.valueOf(l.getConnectionId());
        case 7:
            if (l.isInSwing())
                return "Swing";
            return l.getThreadId();
        }
        return "";
    }

    public SQLRequestLog getRowAt(int rowIndex) {
        return SQLRequestLog.get(SQLRequestLog.getSize() - rowIndex - 1);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {

    }
}
