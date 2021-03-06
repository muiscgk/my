/*
 * DynamicJava - Copyright (C) 1999 Dyade
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions: The above copyright notice and this
 * permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL DYADE BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 * Except as contained in this notice, the name of Dyade shall not be used in advertising or
 * otherwise to promote the sale, use or other dealings in this Software without prior written
 * authorization from Dyade.
 */

package koala.dynamicjava.tree;

import java.util.List;

import koala.dynamicjava.tree.visitor.Visitor;

/**
 * This class represents the import declarations
 * 
 * @author Stephane Hillion
 * @version 1.0 - 1999/04/24
 */

public class ImportDeclaration extends Node {
    /**
     * The name property name
     */
    public final static String NAME = "name";

    /**
     * The package property name
     */
    public final static String PACKAGE = "package";

    /**
     * The name of the imported class or package
     */
    private String name;

    /**
     * Is this declaration import a class or a package
     */
    private boolean pckage;

    /**
     * Creates a new import declaration node
     * 
     * @param ident a list of tokens that represents a package or a class name
     * @param pkg true if this declaration imports a package
     * @exception IllegalArgumentException if ident is null
     */
    public ImportDeclaration(final List ident, final boolean pkg) {
        this(ident, pkg, null, 0, 0, 0, 0);
    }

    /**
     * Creates a new import declaration node
     * 
     * @param ident a list of tokens that represents a package or a class name
     * @param pkg true if this declaration imports a package
     * @param fn the filename
     * @param bl the begin line
     * @param bc the begin column
     * @param el the end line
     * @param ec the end column
     * @exception IllegalArgumentException if ident is null
     */
    public ImportDeclaration(final List ident, final boolean pkg, final String fn, final int bl, final int bc, final int el, final int ec) {
        super(fn, bl, bc, el, ec);

        if (ident == null) {
            throw new IllegalArgumentException("ident == null");
        }

        this.pckage = pkg;
        this.name = TreeUtilities.listToName(ident);
    }

    /**
     * Returns the name of the imported class or package
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the package name
     * 
     * @exception IllegalArgumentException if s is null
     */
    public void setName(final String s) {
        if (s == null) {
            throw new IllegalArgumentException("s == null");
        }

        firePropertyChange(NAME, this.name, this.name = s);
    }

    /**
     * Returns true if the identifier represents a package, false if it represents a
     */
    public boolean isPackage() {
        return this.pckage;
    }

    /**
     * Sets the package property
     */
    public void setPackage(final boolean b) {
        firePropertyChange(PACKAGE, this.pckage, this.pckage = b);
    }

    /**
     * Allows a visitor to traverse the tree
     * 
     * @param visitor the visitor to accept
     */
    @Override
    public Object acceptVisitor(final Visitor visitor) {
        return visitor.visit(this);
    }
}
