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
 
 package org.openconcerto.ui.component.combo;

import org.openconcerto.utils.model.SimpleListDataListener;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ListDataEvent;

public class ISearchableComboPopup<T> extends JPopupMenu {

    private static final int MAXROW = 10;

    private final JList list;
    private int minWitdh = 150;
    private final ISearchableCombo<T> text;

    ISearchableComboPopup(final ListModel listModel, final ISearchableCombo<T> text) {
        // Liste de la popup
        this.list = new JList(listModel);
        this.text = text;
        uiInit();
        // Listeners
        this.list.addMouseMotionListener(new ListMouseMotionHandler());
        // JList always displays visibleRowCount even when fewer items exists
        // so if we put a high number we get a big blank popup
        // instead listen to model change to adjust row count
        this.getListModel().addListDataListener(new SimpleListDataListener() {
            @Override
            public void contentsChanged(ListDataEvent e) {
                // ATTN row count always gets back to zero when the contents change (because of
                // removeAll())
                final int rowCount = Math.min(getListModel().getSize(), 30);
                // checking if rowCount changes doesn't work (one reason is probably that we're
                // called before Swing and so setVisible displays an empty list)
                ISearchableComboPopup.this.list.setVisibleRowCount(rowCount);
                if (rowCount > 0 && isVisible()) {
                    // since "visible row count" is not dynamic
                    setVisible(false);
                    setVisible(true);
                }
            }
        });
    }

    private ISearchableCombo<T> getCombo() {
        return this.text;
    }

    private ListModel getListModel() {
        return this.list.getModel();
    }

    private void uiInit() {
        this.list.setFocusable(false);
        this.list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.list.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                validateSelection();
            }
        });
        this.list.setCellRenderer(new DefaultListCellRenderer() {
            @SuppressWarnings("unchecked")
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                // MAYBE optimize
                final JLabel comp = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                comp.setFont(getCombo().getFont());
                if (value instanceof Action) {
                    comp.setText((String) ((Action) value).getValue(Action.NAME));
                    comp.setIcon(null);
                } else {
                    final ISearchableComboItem<T> val = (ISearchableComboItem<T>) value;
                    comp.setText(val.asString());
                    comp.setIcon(getCombo().getIcon(val));
                    if (getCombo().isEmptyItem(val)) {
                        comp.setFont(comp.getFont().deriveFont(Font.ITALIC));
                    }
                }
                return comp;
            }
        });
        // Scroller
        JScrollPane scroller = new JScrollPane(this.list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setFocusable(false);
        scroller.getVerticalScrollBar().setFocusable(false);
        scroller.setBorder(null);
        // Popup
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorderPainted(true);
        setBorder(BorderFactory.createLineBorder(Color.black));
        setOpaque(true);
        add(scroller);

        setFocusable(false);
    }

    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();

        int width = d.width;
        if (width > 500)
            width = 500;
        width = Math.max(width, this.minWitdh) + 2;

        Dimension newD = new Dimension(width, d.height);
        return newD;
    }

    public void setMinWith(int i) {
        this.minWitdh = i;
    }

    protected class ListMouseMotionHandler extends MouseMotionAdapter {
        public void mouseMoved(MouseEvent anEvent) {
            updateListBoxSelection(anEvent.getPoint());
        }
    }

    protected void updateListBoxSelection(Point location) {
        this.updateListBoxSelection(location, true);
    }

    private void updateListBoxSelection(Point location, boolean shouldScroll) {
        if (this.list == null)
            return;
        int index = this.list.locationToIndex(location);
        if (index == -1) {
            if (location.y < 0)
                index = 0;
            else
                index = this.getListModel().getSize() - 1;
        }
        if (this.list.getSelectedIndex() != index) {
            // MAYBE optimize (already faster than setSelectedIndex())
            this.list.getSelectionModel().setSelectionInterval(index, index);
            if (shouldScroll)
                this.list.ensureIndexIsVisible(index);
        }
    }

    public void selectNext() {
        int i = this.list.getSelectedIndex() + 1;
        if (i < this.getListModel().getSize()) {
            this.list.setSelectedIndex(i);
            this.list.ensureIndexIsVisible(i);
        }
    }

    public void selectNextPage() {
        int i = Math.min(MAXROW + Math.max(this.list.getSelectedIndex(), 0), this.getListModel().getSize() - 1);
        if (i < this.getListModel().getSize()) {
            this.list.setSelectedIndex(i);
            this.list.ensureIndexIsVisible(i);
        }
    }

    public void selectPrevious() {
        int i = this.list.getSelectedIndex() - 1;
        if (i >= 0) {
            this.list.setSelectedIndex(i);
            this.list.ensureIndexIsVisible(i);
        } else {
            this.setVisible(false);
        }
    }

    public void selectPreviousPage() {
        int i = Math.max(0, this.list.getSelectedIndex() - MAXROW);
        this.list.setSelectedIndex(i);
        this.list.ensureIndexIsVisible(i);
    }

    final void validateSelection() {
        final Object sel = this.list.getSelectedValue();
        // if no selection, don't change the combo
        if (sel != null) {
            if (sel instanceof ISearchableComboItem) {
                this.getCombo().setValue(((ISearchableComboItem<T>) sel).getOriginal());
            } else if (sel instanceof Action) {
                ((Action) sel).actionPerformed(new ActionEvent(this.getCombo(), ActionEvent.ACTION_PERFORMED, this.getCombo().getName()));
            } else
                throw new IllegalStateException("unknown selection: " + sel);
            this.setVisible(false);
        }
    }

    public void open() {
        // si on est pas déjà affiché
        // afficher même qd pas d'items : si l'user clique il faut qu'il voit la liste même vide
        if (!this.isShowing())
            this.show(this.getCombo(), 0, this.getCombo().getBounds().height);
        this.list.setSelectedValue(this.getCombo().getSelection(), true);
    }

    public void close() {
        this.setVisible(false);
    }

}
