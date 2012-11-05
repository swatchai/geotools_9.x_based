package org.geotools.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

public class NestedAttributeImpl extends ComplexAttributeImpl implements Collection<Attribute> {

	private Iterator<Attribute> iterator;

	public NestedAttributeImpl(
			AttributeDescriptor descriptor, Identifier id, Iterator<Attribute> nestedFeatures) {
		super(new ArrayList<Property>(), descriptor, id);
		this.iterator = nestedFeatures;
	}
	
	@Override
	public Collection<? extends Property> getValue() {
		return new ArrayList<Property>();
	}
	
	@Override
	protected Collection properties() {
        return new ArrayList<Property>();
    }
	
	@Override
    public Collection<Property> getProperties(Name name) {
		return new ArrayList<Property>();
    }
    
	@Override
    public Property getProperty(String name) {        
        return null;
    }

	@Override
	public int size() {
		// TODO Auto-generated method stub
		return 0;
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
	public Iterator<Attribute> iterator() {
		return iterator;
	}

	@Override
	public Object[] toArray() {
		// TODO Auto-generated method stub
		return null;
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
	public boolean add(Attribute e) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addAll(Collection<? extends Attribute> c) {
		// TODO Auto-generated method stub
		return false;
	}
}
