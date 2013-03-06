package org.geotools.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.geotools.data.complex.DataAccessMappingFeatureIterator;
import org.geotools.data.complex.IMappingFeatureIterator;
import org.geotools.data.complex.NestedAttributeIterator;
import org.geotools.data.complex.NestedFeaturesCollection;
import org.opengis.feature.Attribute;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.Feature;
import org.opengis.feature.IllegalAttributeException;
import org.opengis.feature.Property;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.ComplexType;
import org.opengis.feature.type.Name;
import org.opengis.filter.identity.Identifier;

public class NestedAttributeCollection implements Collection<Property> {

	private NestedAttributeIterator iterator;
	
//	private int counter;
	
	public NestedAttributeCollection(NestedAttributeIterator iterator) {
		this.iterator = iterator;
//		counter = 0;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean contains(Object o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Iterator<Property> iterator() {
		iterator.removeSkippedCount();
		return iterator;
	}

	@Override
	public <T> T[] toArray(T[] a) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public boolean remove(Object o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean add(Property e) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addAll(Collection<? extends Property> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object[] toArray() {
		// TODO Auto-generated method stub
		return null;
	}
//	
//
//	@Override
//	public Property get(int index) {	
//		Iterator<Property> iterator = iterator();
//		Property prop = null;
//		while (counter <= index && iterator.hasNext()) {
//			if (counter == index) {
//			    prop = iterator.next();
//			} else {
//				iterator.next();
//			}
//			counter++;
//		}
//
//		// reset if the end is reached
//		if (index < counter) {
//			counter = 0;		
//			if (iterator)
//		}
//		
//		return prop;
//	}
	
	@Override
	public int size() {
		return 1;
	}
}
