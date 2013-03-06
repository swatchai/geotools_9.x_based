/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2007-2011, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */

package org.geotools.feature.xpath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.model.NodeIterator;
import org.apache.commons.jxpath.ri.model.NodePointer;
import org.geotools.data.complex.NestedAttributeIterator;
import org.geotools.feature.NestedAttributeCollection;
import org.geotools.feature.NestedAttributeImpl;
import org.opengis.feature.Attribute;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.Property;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;

/**
 * Special node iterator for {@link Attribute}.
 * 
 * @author Justin Deoliveira (The Open Planning Project)
 * @author Gabriel Roldan (Axios Engineering)
 * 
 *
 *
 *
 *
 * @source $URL$
 */
public class AttributeNodeIterator implements NodeIterator {

    /**
     * The feature node pointer
     */
    AttributeNodePointer pointer;

    /**
     * The feature.
     */
    ComplexAttribute feature;

    Iterator<? extends Property> children;
    
    Name name;

    /**
     * current position
     */
    int position;

    public AttributeNodeIterator(AttributeNodePointer pointer) {
        this.pointer = pointer;
        feature = (ComplexAttribute) pointer.getImmediateNode();
        children = feature.getValue().iterator();
        position = 1;
    }

    public AttributeNodeIterator(AttributeNodePointer pointer, Name name) {
        this.pointer = pointer;
        this.name = name;
        feature = (ComplexAttribute) pointer.getImmediateNode();
        setIterator();
    }
    
    private void setIterator() {
    	AttributeDescriptor descriptor = feature.getDescriptor();
    	Name attName = descriptor == null ? feature.getType().getName() : descriptor.getName();
        if (attName.equals(name)) {
            children = Collections.<Property>singletonList(feature).iterator();
        } else {
			if (feature instanceof NestedAttributeImpl) {
				try {
					children = ((NestedAttributeImpl) feature).iterator(name);
				} catch (IOException e) {
					children = null;
				}
			} else {
				Collection<Property> properties = feature.getProperties(name);
				children = properties.iterator();
			}
        }
        position = (children != null && children.hasNext()) ? 1 : 0;
    }

    public int getPosition() {
        return position;
    }

    public boolean setPosition(int position) {
    	if ((this.position == (position - 1)) && children.hasNext()) {
    		this.position++;
    	}
    	if (this.position == position) {
    		return true;
    	}    	
//    	if (children instanceof NestedAttributeIterator) {
//    		((NestedAttributeIterator) children).close();
//    	}
    	return false;
    }

    public NodePointer getNodePointer() {
        Attribute attribute = (Attribute) children.next();        
        Name name = attribute.getDescriptor().getName();
        QName qname = new QName(name.getNamespaceURI(), name.getLocalPart());
        return new AttributeNodePointer(pointer, attribute, qname);
    }

}
