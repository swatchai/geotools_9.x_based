package org.geotools.feature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.geotools.data.FeatureSource;
import org.geotools.data.complex.AppSchemaDataAccessRegistry;
import org.geotools.data.complex.DataAccessMappingFeatureIterator;
import org.geotools.data.complex.DataAccessRegistry;
import org.geotools.data.complex.IMappingFeatureIterator;
import org.geotools.data.complex.MappingFeatureSource;
import org.geotools.data.complex.NestedAttributeIterator;
import org.geotools.data.complex.NestedFeaturesCollection;
import org.geotools.data.joining.JoiningNestedAttributeMapping;
import org.geotools.data.joining.XlinkSourceIterator;
import org.opengis.feature.Attribute;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.Feature;
import org.opengis.feature.IllegalAttributeException;
import org.opengis.feature.Property;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.ComplexType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.Identifier;

public class NestedAttributeImpl extends ComplexAttributeImpl implements Collection<Property> {

	private NestedAttributeIterator iterator;
//	private JoiningNestedAttributeMapping mapping;
//	private List<DataAccessMappingFeatureIterator> nestedFeaturesList;
//	private List<Attribute> rootElements;
//	private List<Object> foreignIds;
	private List<Object> foreignIds;
	private Map<Name, NestedAttributeIterator> filteredInstances;

	public NestedAttributeImpl(
			AttributeDescriptor descriptor, Identifier id, DataAccessMappingFeatureIterator nestedFeatures,
			Attribute wrapper, List<Object> foreignIds) {
		super(new ArrayList<Property>(), descriptor, id);
		
		nestedFeatures.addSkippedCount();
		this.iterator = new NestedAttributeIterator(nestedFeatures, wrapper, foreignIds);
		
		this.foreignIds = foreignIds;
		
		this.filteredInstances = new HashMap<Name, NestedAttributeIterator>();
		
//		this.mapping = mapping;
//		
//		this.nestedFeaturesList = new ArrayList<DataAccessMappingFeatureIterator>();
//		this.nestedFeaturesList.add(nestedFeatures);
//		this.rootElements = new ArrayList<Attribute>();
//		this.rootElements.add(wrapper);
//		this.foreignIds = foreignIds;
	}
	
//	public void resetIterator() throws IOException {
//		for (DataAccessMappingFeatureIterator iterator : nestedFeaturesList) {
//			iterator.reset();
//		}
//	}
	
	public NestedAttributeImpl(AttributeDescriptor descriptor,
			FeatureId id,
			XlinkSourceIterator nestedFeatures,
			Attribute wrapper, List<Object> foreignIds,
			Expression href) {
        super(new ArrayList<Property>(), descriptor, id);
		
		this.iterator = new NestedAttributeIterator(nestedFeatures, wrapper, foreignIds, href);
		
		this.foreignIds = foreignIds;
		
		this.filteredInstances = new HashMap<Name, NestedAttributeIterator>();
	}

	public NestedAttributeImpl(
			AttributeDescriptor descriptor, Identifier id, 
			Attribute wrapper, List<Object> foreignIds) {
		super(new ArrayList<Property>(), descriptor, id);
		
		
		this.iterator = new NestedAttributeIterator(null, wrapper, foreignIds);
		
		this.foreignIds = foreignIds;
		
		this.filteredInstances = new HashMap<Name, NestedAttributeIterator>();
		
//		this.mapping = mapping;
//		
//		this.nestedFeaturesList = new ArrayList<DataAccessMappingFeatureIterator>();
//		this.nestedFeaturesList.add(nestedFeatures);
//		this.rootElements = new ArrayList<Attribute>();
//		this.rootElements.add(wrapper);
//		this.foreignIds = foreignIds;
	}
	
	@Override
	public Collection<? extends Property> getValue() {
		return new ArrayList<Property>();
//		NestedAttributeIterator iterator = new NestedAttributeIterator(nestedFeaturesList.get(0), rootElements.get(0), foreignIds);			
//		for (int i = 1; i < nestedFeaturesList.size(); i++) {
//			iterator.add(nestedFeaturesList.get(i), rootElements.get(i));
//		}
//		return new NestedAttributeCollection(iterator);
	}
	
	@Override
	protected Collection properties() {
		return getValue();
    }
	
	@Override
	public Collection<Property> getProperties() {
		return properties();
    }
	
	@Override
    public Collection<Property> getProperties(Name name) {
		return properties();
//		for (DataAccessMappingFeatureIterator features : nestedFeaturesList) {
//			if (features.getTargetFeatureName().equals(name)) {
//				NestedAttributeIterator iterator = new NestedAttributeIterator(features, null, foreignIds);	
//				return new NestedAttributeCollection(iterator);
//			}
//		}
//		return null;
//		NestedAttributeIterator iterator = null;
//		int i = 0;
//		for (Attribute root : rootElements) {
//			if (name.equals(root.getName())) {
//				if (iterator == null) {
//					iterator = new NestedAttributeIterator(nestedFeaturesList.get(i), rootElements.get(i), foreignIds);		
//				} else {
//					iterator.add(nestedFeaturesList.get(i), rootElements.get(i));
//				}
//			}
//			i++;
//		}
//		if (iterator != null) {
//			return new NestedAttributeCollection(iterator);
//		}
//		return new ArrayList<Property>();
    }
    
	@Override
    public Property getProperty(String name) { 
		//TODO: throw exception to use iterator?
        return null;
    }
	
	@Override
    public Property getProperty(Name name) { 
		//TODO: throw exception to use iterator?
        return null;
    }

	public void addIterator(DataAccessMappingFeatureIterator nestedFeatures, Attribute parentElement) {
		// TODO Auto-generated method stub
//		this.nestedFeaturesList.add(nestedFeatures);
		nestedFeatures.addSkippedCount();
		this.iterator.add(nestedFeatures, parentElement);
//		this.rootElements.add(parentElement);
	}

	@Override
	public int size() {
		// TODO Auto-generated method stub
//		return 0;
		return 1;
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
//		NestedAttributeIterator iterator = new NestedAttributeIterator(nestedFeaturesList.get(0), rootElements.get(0), foreignIds);			
//		for (int i = 1; i < nestedFeaturesList.size(); i++) {
//			iterator.add(nestedFeaturesList.get(i), rootElements.get(i));
//		}
		iterator.removeSkippedCount();
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
		for (NestedAttributeIterator features : filteredInstances.values()) {
			features.close();
		}
		this.filteredInstances = null;
		this.iterator.close();
		this.iterator = null;
		
//		nestedFeaturesList = null;
		this.descriptor = null;
		this.foreignIds = null;
//		this.rootElements = null;
	}

	@Override
	public boolean add(Property e) {
		// TODO Auto-generated method stub
		if (e instanceof Attribute) {
			return this.iterator.add((Attribute) e);
		}
		return false;
	}

	@Override
	public boolean addAll(Collection<? extends Property> c) {
		// TODO Auto-generated method stub
		return false;
	}

	public Iterator<? extends Property> iterator(Name name) throws IOException {
		NestedAttributeIterator iterator = this.filteredInstances.get(name);
		if (iterator == null) {
			DataAccessMappingFeatureIterator nestedFeatures = this.iterator
					.getIterator(name);
			if (nestedFeatures != null && nestedFeatures.isAvailable()) {
				nestedFeatures.removeSkippedCount();
				iterator = new NestedAttributeIterator(nestedFeatures, null,
						foreignIds);
				filteredInstances.put(name, iterator);
			}
		}
		if (iterator == null) {
			// look for normal attribute
			Collection<Property> properties = this.iterator.getProperties(name);
			return properties.iterator();
		}
		return iterator;
//		if (iterator != null) {
//			Name typeName = iterator.getMappingName();
//			if (typeName == null) {
//				typeName = name;
//			}
//		    return mapping.getIterator(typeName, caller, 
//		    		iterator.getReprojection(), iterator.getSelectedProperties().get(mapping), iterator.getIncludeMandatory());
//		}
//		return null;
	}

	public void addIterator(XlinkSourceIterator nestedFeatures,
			Attribute nestedFeaturesElement, Expression href) {
		this.iterator.add(nestedFeatures, nestedFeaturesElement, href);
	}
}
