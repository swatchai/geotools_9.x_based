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
import org.geotools.data.complex.filter.XPath;
import org.geotools.data.complex.filter.XPath.StepList;
import org.geotools.data.joining.XlinkSourceIterator;
import org.geotools.feature.AppSchemaFeatureFactoryImpl;
import org.geotools.feature.GeometryAttributeImpl;
import org.geotools.feature.Types;
import org.geotools.feature.type.GeometryTypeImpl;
import org.opengis.feature.Attribute;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.Property;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.filter.expression.Expression;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.xml.sax.Attributes;

import com.vividsolutions.jts.geom.Geometry;
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
public class NestedAttributeIterator implements Iterator<Property> {
	
	class IteratorToWrapper {

		IMappingFeatureIterator iterator;
		Attribute parentElement;
		private Expression xlinkHref;
		
		public IteratorToWrapper(IMappingFeatureIterator iterator2,
				Attribute parentElement2) {
			this.iterator = iterator2;
			this.parentElement = parentElement2;
		}
		
		public IteratorToWrapper(IMappingFeatureIterator iterator2,
				Attribute parentElement2, Expression hrefExpression) {
			this.iterator = iterator2;
			this.parentElement = parentElement2;
			this.xlinkHref = hrefExpression;
		}
		
		public boolean isXlinkHref() {
			return xlinkHref != null;
		}
	}	

    private List<IteratorToWrapper> iterators;
    
    private AppSchemaFeatureFactoryImpl ff;

	private List<Object> groupingForeignKeys;

	private int iteratorIndex;

	public NestedAttributeIterator(DataAccessMappingFeatureIterator iterator,
			Attribute parentElement, List<Object> groupingForeignKeys) {
		this.iterators = new ArrayList<IteratorToWrapper>();
		this.iterators.add(
				new IteratorToWrapper(iterator, parentElement));
		this.ff = new AppSchemaFeatureFactoryImpl();
		this.groupingForeignKeys = groupingForeignKeys;
		iteratorIndex = 0;
	}
	
	public NestedAttributeIterator(
			XlinkSourceIterator nestedFeatures,
			Attribute parentElement, List<Object> groupingForeignKeys, Expression href) {
		this.iterators = new ArrayList<IteratorToWrapper>();
		this.iterators.add(
				new IteratorToWrapper(nestedFeatures, parentElement, href));
		this.ff = new AppSchemaFeatureFactoryImpl();
		this.groupingForeignKeys = groupingForeignKeys;
		iteratorIndex = 0;
	}

	public void add(DataAccessMappingFeatureIterator iterator,
			Attribute parentElement) {
		this.iterators.add(
				new IteratorToWrapper(iterator, parentElement));
	}
	
	public void add(IMappingFeatureIterator nestedFeatures,
			Attribute parentElement, Expression hrefExpression) {
		this.iterators.add(
				new IteratorToWrapper(nestedFeatures, parentElement, hrefExpression));
	}
	
	public void removeSkippedCount() {
		for (IteratorToWrapper pair : iterators) {
			if (pair.iterator != null && pair.iterator instanceof DataAccessMappingFeatureIterator) {
			    ((DataAccessMappingFeatureIterator) pair.iterator).removeSkippedCount();
			}
		}
	}

	@Override
	public boolean hasNext() {
		while (iteratorIndex < iterators.size()) {
		    IteratorToWrapper pair = iterators.get(iteratorIndex);

		    DataAccessMappingFeatureIterator featureIterator = null;
			if (pair.iterator != null) {
				if (pair.iterator instanceof DataAccessMappingFeatureIterator) {
					featureIterator = (DataAccessMappingFeatureIterator) pair.iterator;
				} else if (pair.iterator instanceof XlinkSourceIterator) {
					featureIterator = ((XlinkSourceIterator)pair.iterator).getComplexFeatureIterator();
				} else {
					// throw exception
				}
					while (featureIterator.getSkippedCount() > 0) {
						// features should be skipped when automatic xlink:href
						// is performed
						if (featureIterator.hasNext()) {
							List<Object> skippedForeignIds = featureIterator
									.getForeignIdValues(featureIterator.curSrcFeature);
							try {
								featureIterator.skip();
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}

							while (featureIterator.hasNext()
									&& featureIterator
											.checkForeignIdValues(skippedForeignIds)) {
								try {
									featureIterator.skip();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							featureIterator.removeSkippedCount();
						}
					}
					if (featureIterator.hasNext()
							&& featureIterator
									.checkForeignIdValues(groupingForeignKeys)) {
						return true;
					}
//				} else {
//					// must be xlink href
//					return pair.iterator.hasNext();
//				}
		    } else {
		    	// this is just a normal attribute, not a nested features iterator
		    	return true;
		    }
			// not found, try the next one?
			iteratorIndex++;
		}		
		return false;
	}

	@Override
	public Attribute next() {
		// TODO Auto-generated method stub
		Feature feature = null;
		Attribute wrapper = null;
		IteratorToWrapper pair = iterators.get(iteratorIndex);
		int currIndex = iteratorIndex;
		iteratorIndex = 0;
		if (pair.iterator == null) {
			// not a nested attribute
			iterators.remove(currIndex);
			return pair.parentElement;
		} else {
			// nested features
			if (pair.iterator.hasNext()) {
				feature = pair.iterator.next();
				if (pair.parentElement != null) {
					wrapper = ff.clone(pair.parentElement);
				}
			}
		}
		if (wrapper != null) {
			if (pair.isXlinkHref()) {		        
	            // Make sure the same value isn't already set
	            // in case it comes from a denormalized view for many-to-many relationship.
	            // (1) Get the first existing value
                Object existingValue = wrapper.getUserData().get(Attributes.class);
                boolean exists = false;
                if (existingValue != null) {
	                assert existingValue instanceof HashMap;
	                ((Map) existingValue).put(ComplexFeatureConstants.XLINK_HREF_NAME,
	                		pair.xlinkHref.evaluate(feature));
//	                existingValue = ((Map) existingValue).get(ComplexFeatureConstants.XLINK_HREF_NAME);
//	                if (existingValue != null) {
//	                    Object hrefValue = pair.xlinkHref.evaluate(wrapper);
//	                    if (hrefValue != null && hrefValue.equals(existingValue)) {
//	                            // (2) if one of the new values matches the first existing value,
//	                            // that means this comes from a denormalized view,
//	                            // and this set has already been set
//	                        exists = true;
//	                    }
//	                }
	            }
//	            if (!exists) {
//	                Attribute instance = xpathAttributeBuilder.set(target, xpath, null, null,
//	                        targetNodeType, true, null, descriptors);
//	                setClientProperties(instance, singleVal, clientPropsMappings);
//	            }
			} else if (Types.isSimpleContentType(wrapper.getType()) 
					|| Types.isGeometryType(wrapper.getType())) {
				Collection<Property> properties = feature.getProperties();
				if (!properties.isEmpty()) {
					Property prop = null;
					while (prop == null && properties.iterator().hasNext()) {
						Property nextProp = properties.iterator().next();
						if (!nextProp.getName().equals(ComplexFeatureConstants.FEATURE_CHAINING_LINK_NAME)) {
							prop = nextProp;
							Object value = prop.getValue();
							if (value != null) {
								wrapper.setValue(value);
								
//								if (wrapper.getDescriptor() instanceof GeometryDescriptor) {
//									GeometryDescriptor newDescriptor;
//									GeometryType geomType = (GeometryType) wrapper.getType();
//									Object userData = ((Geometry)value).getUserData();
//									CoordinateReferenceSystem crs;
//									if (userData != null && userData instanceof CoordinateReferenceSystem) {
//										crs = (CoordinateReferenceSystem) userData;
//									}
//									GeometryType newGeomType = new GeometryTypeImpl(geomType.getName(),
//											geomType.getBinding(), crs, geomType.isIdentified(),
//											geomType.isAbstract(), geomType.getRestrictions(),
//											geomType.getSuper(), geomType.getDescription());
//									newDescriptor = descriptorFactory.createGeometryDescriptor(
//											newGeomType, currDescriptor.getName(),
//											currDescriptor.getMinOccurs(),
//											currDescriptor.getMaxOccurs(), currDescriptor.isNillable(),
//											null);
//								}
							}
							XPath.mergeClientProperties(wrapper,
									prop.getUserData());						
							
						}
					}
				}
			} else if (wrapper instanceof ComplexAttribute) {
				ArrayList<Feature> featureList = new ArrayList<Feature>();
				featureList.add(feature);
				wrapper.setValue(featureList);
			}
			return wrapper;	
		} else {
			// just return the feature.. this it to cater for xpath filtering using AttributeNodeIterator
			return feature;
		}
	}

	@Override
	public void remove() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("NestedAttributeIterator.remove() not supported!");
	}	

	public void close() {
		for (IteratorToWrapper pair : iterators) {
			if (pair.iterator != null && pair.iterator.isAvailable()) {
			    pair.iterator.close();
			}
		}
		iterators = null;
	}

	public DataAccessMappingFeatureIterator getIterator(Name name) {
		for (IteratorToWrapper pair : iterators) {
			if (pair.iterator != null) {
				DataAccessMappingFeatureIterator featureIterator = null;
				if (pair.iterator instanceof DataAccessMappingFeatureIterator) {
					featureIterator = (DataAccessMappingFeatureIterator) pair.iterator;
				} else {
					featureIterator = ((XlinkSourceIterator)pair.iterator).getComplexFeatureIterator();
				}
				if (featureIterator.getTargetFeature().getName().equals(name)) {
					return featureIterator;
				}
			}
		}
		return null;
	}

	public boolean add(Attribute e) {
		IteratorToWrapper pair = new IteratorToWrapper(null, e);
		return this.iterators.add(pair);
	}

	public Collection<Property> getProperties(Name name) {
		ArrayList<Property> properties = new ArrayList<Property>();
		for (IteratorToWrapper pair : iterators) {
			if (pair.iterator == null) {
				properties.add(pair.parentElement);
			}
		}
		return properties;
	}
}
