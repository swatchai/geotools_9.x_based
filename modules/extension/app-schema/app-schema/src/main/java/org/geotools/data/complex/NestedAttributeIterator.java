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

package org.geotools.data.complex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.geotools.data.Query;
import org.geotools.data.complex.filter.XPath.StepList;
import org.geotools.feature.AppSchemaFeatureFactoryImpl;
import org.geotools.feature.Types;
import org.opengis.feature.Attribute;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.type.Name;
/**
 * A Feature iterator that operates over the FeatureSource of a
 * {@linkplain org.geotools.data.complex.FeatureTypeMapping} that is of a simple content type, e.g.
 * representing a gml:name element. This is required for feature chaining for such types to reduce
 * the need of creating an additional database view when simple element values come from another
 * table. Therefore this iterator should have a method that return attributes that are to be chained
 * directly in another feature type.
 * 
 * @author Rini Angreani (CSIRO Earth Science and Resource Engineering)
 * 
 * @source $URL:
 *         http://svn.osgeo.org/geotools/trunk/modules/extension/app-schema/app-schema/src/main
 *         /java/org/geotools/data/complex/MappingAttributeIterator.java $
 *         http://svn.osgeo.org/geotools/trunk/modules/unsupported/app-schema/app-schema/src/main
 *         /java/org/geotools/data/complex/MappingAttributeIterator.java $
 * @since 2.7
 */
public class NestedAttributeIterator implements Iterator<Attribute> {
	
	class IteratorToWrapper {

		IMappingFeatureIterator iterator;
		Attribute parentElement;
		
		public IteratorToWrapper(IMappingFeatureIterator iterator2,
				Attribute parentElement2) {
			this.iterator = iterator2;
			this.parentElement = parentElement2;
		}
	}

    private List<IteratorToWrapper> iterators;
    
    private AppSchemaFeatureFactoryImpl ff;

	public NestedAttributeIterator(IMappingFeatureIterator iterator,
			Attribute parentElement) {
		this.iterators = new ArrayList<IteratorToWrapper>();
		this.iterators.add(
				new IteratorToWrapper(iterator, parentElement));
		this.ff = new AppSchemaFeatureFactoryImpl();
	}
	
	public void add(IMappingFeatureIterator iterator,
			Attribute parentElement) {
		this.iterators.add(
				new IteratorToWrapper(iterator, parentElement));
	}

	@Override
	public boolean hasNext() {	
		for (IteratorToWrapper pair : iterators) {
			if (pair.iterator.hasNext()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Attribute next() {
		// TODO Auto-generated method stub
		Feature feature = null;
		Attribute wrapper = null;
		for (IteratorToWrapper pair : iterators) {
			if (pair.iterator.hasNext()) {
				// if it's been closed, it would get the next one
				feature = pair.iterator.next();
				wrapper = ff.clone(pair.parentElement);
				break;
			}
		}
		if (wrapper != null) {
			if (wrapper instanceof ComplexAttribute) {
				ArrayList<Feature> featureList = new ArrayList<Feature>();
				featureList.add(feature);
				wrapper.setValue(featureList);
			} else {
				Collection<Property> properties = feature.getProperties();
				if (!properties.isEmpty()) {
					Property prop = properties.iterator().next();
					Object value = prop.getValue();
					if (value != null) {
						wrapper.setValue(value);
					}
				}
			}
		}
		return wrapper;	
	}

	@Override
	public void remove() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("NestedAttributeIterator.remove() not supported!");
	}

	public void close() {
		iterators = null;
	}
}
