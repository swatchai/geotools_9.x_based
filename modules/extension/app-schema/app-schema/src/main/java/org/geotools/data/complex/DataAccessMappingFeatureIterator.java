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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.eclipse.xsd.XSDElementDeclaration;
import org.geotools.data.DataAccess;
import org.geotools.data.DataSourceException;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.complex.config.NonFeatureTypeProxy;
import org.geotools.data.complex.filter.XPath;
import org.geotools.data.complex.filter.XPath.Step;
import org.geotools.data.complex.filter.XPath.StepList;
import org.geotools.data.joining.JoiningNestedAttributeMapping;
import org.geotools.data.joining.JoiningQuery;
import org.geotools.data.joining.XlinkSourceIterator;
import org.geotools.feature.AttributeBuilder;
import org.geotools.feature.ComplexAttributeImpl;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureImpl;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.NestedAttributeImpl;
import org.geotools.feature.Types;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.FilterAttributeExtractor;
import org.geotools.gml2.bindings.GML2EncodingUtils;
import org.geotools.jdbc.JDBCFeatureSource;
import org.geotools.jdbc.JDBCFeatureStore;
import org.geotools.jdbc.JoiningJDBCFeatureSource;
import org.geotools.referencing.CRS;
import org.geotools.util.CheckedArrayList;
import org.geotools.xs.XS;
import org.opengis.feature.Attribute;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyType;
import org.opengis.filter.Filter;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.xml.sax.Attributes;

/**
 * A Feature iterator that operates over the FeatureSource of a
 * {@linkplain org.geotools.data.complex.FeatureTypeMapping} and produces Features of the output
 * schema by applying the mapping rules to the Features of the source schema.
 * <p>
 * This iterator acts like a one-to-one mapping, producing a Feature of the target type for each
 * feature of the source type.
 *
 * @author Gabriel Roldan (Axios Engineering)
 * @author Ben Caradoc-Davies (CSIRO Earth Science and Resource Engineering)
 * @author Rini Angreani (CSIRO Earth Science and Resource Engineering)
 * @author Russell Petty (GeoScience Victoria)
 * @version $Id$
 *
 *
 *
 * @source $URL$
 *         http://svn.osgeo.org/geotools/trunk/modules/unsupported/app-schema/app-schema/src/main
 *         /java/org/geotools/data/complex/DaLtaAccessMappingFeatureIterator.java $
 * @since 2.4
 */
public class DataAccessMappingFeatureIterator extends AbstractMappingFeatureIterator {
	
    /**
     * Hold on to iterator to allow features to be streamed.
     */
    private FeatureIterator<? extends Feature> sourceFeatureIterator;

    /**
     * Reprojected CRS from the source simple features, or null
     */
    protected CoordinateReferenceSystem reprojection;

    /**
     * This is the feature that will be processed in next()
     */
    protected Feature curSrcFeature;

    protected FeatureSource<? extends FeatureType, ? extends Feature> mappedSource;

    protected FeatureCollection<? extends FeatureType, ? extends Feature> sourceFeatures;

    protected List<Expression> foreignIds = null;

    /**
     * True if joining is turned off and pre filter exists. There's a need to run extra query to get
     * features by id because they might come from denormalised view. The rows might not match the
     * filter therefore doesn't exist in the mapped source but match the id of other rows.
     */
    private boolean isFiltered = false;
    
    private ArrayList<String> filteredFeatures;
    /**
     * Temporary/experimental changes for enabling subsetting for isList only. 
     */
    protected Filter listFilter;

//	private List<Object> groupingForeignIds;

	private boolean isBuildingXlinkHref;

	private long millis;
	
	private long cleanmillis;

//	private boolean isExistingIterator = false;

	private int skipCounter = 0;
	
    public DataAccessMappingFeatureIterator(AppSchemaDataAccess store, FeatureTypeMapping mapping,
            Query query, boolean isFiltered) throws IOException {  	

    	this(store, mapping, query, null);
        this.isFiltered = isFiltered;
        if (isFiltered) {
            filteredFeatures = new ArrayList<String>();
        }
        this.isBuildingXlinkHref = false;
    }
    
    public DataAccessMappingFeatureIterator(AppSchemaDataAccess store, FeatureTypeMapping mapping,
            Query query) throws IOException {
        this(store, mapping, query, null);
    }

    /**
     *
     * @param store
     * @param mapping
     *            place holder for the target type, the surrogate FeatureSource and the mappings
     *            between them.
     * @param query
     *            the query over the target feature type, that is to be unpacked to its equivalent
     *            over the surrogate feature type.
     * @throws IOException
     */
    public DataAccessMappingFeatureIterator(AppSchemaDataAccess store, FeatureTypeMapping mapping,
            Query query, Query unrolledQuery) throws IOException {
        super(store, mapping, query, unrolledQuery);
    }

    public DataAccessMappingFeatureIterator(
			DataAccessMappingFeatureIterator features) throws IOException {
		super(features.store, features.mapping, features.query, null);
	}

	@Override
    public boolean hasNext() {
        boolean exists = !isNextSourceFeatureNull();

        if (getNextSrc()) {
            if (featureCounter < maxFeatures) {
                if (!exists && getSourceFeatureIterator() != null
                        && getSourceFeatureIterator().hasNext()) {
                    this.curSrcFeature = getSourceFeatureIterator().next();
                    exists = true;
                }
                if (exists && filteredFeatures != null) {
                    // get the next one if this row has already been added to the target
                    // feature from setNextFilteredFeature
                    while (exists
                            && filteredFeatures.contains(extractIdForFeature(this.curSrcFeature))) {
                        if (getSourceFeatureIterator() != null
                                && getSourceFeatureIterator().hasNext()) {
                            this.curSrcFeature = getSourceFeatureIterator().next();
                            exists = true;
                        } else {
                            exists = false;
                        }
                    }
                }
                // HACK HACK HACK
                // evaluate filter that applies to this list as we want a subset
                // instead of full result
                // this is a temporary solution for Bureau of Meteorology
                // requirement for timePositionList
                if (listFilter != null) {
                    while (exists && !listFilter.evaluate(curSrcFeature)) {
                        // only add to subset if filter matches value
                        if (getSourceFeatureIterator() != null
                                && getSourceFeatureIterator().hasNext()) {
                            this.curSrcFeature = getSourceFeatureIterator().next();
                            exists = true;
                        } else {
                            exists = false;
                        }
                    }
                }
                // END OF HACK
            } else {
                exists = false;
            }
        }
        
        setNextSrc(false);

        if (!exists) {
        	if (isAvailable()) {    
                LOGGER.finest("no more features, produced " + featureCounter);
                closeSourceFeatures();
//                close();
        	} else {
        		curSrcFeature = null;
        	}
//            clean();
        }         

        return exists;
    }
        
//    public int size() {
//    	// if already counted, return it
//		if (size > -1) {
//			return size;
//		} else {
//			// initiate otherwise
//			
//		}
//		
//    }

    protected FeatureIterator<? extends Feature> getSourceFeatureIterator() {
        return sourceFeatureIterator;
    }

    protected boolean isSourceFeatureIteratorNull() {
        return getSourceFeatureIterator() == null;
    }

    protected Object peekValue(Object source, Expression prop) {
        Object o = prop.evaluate (source);
        if (o instanceof Attribute) {
            o = ((Attribute) o).getValue();
        }
        return o;
    }
    
    public Object peekNextValue(Expression prop) {
        return peekValue(curSrcFeature , prop);
    }

    /**
     * Only used for Joining, to make sure that rows with different foreign id's
     * aren't interpreted as one feature and merged.
     */
    public void setForeignIds(List<Expression> ids) {
        foreignIds = ids;
    }
    
    /**
     * Only used for Joining, to make sure that rows with different foreign id's
     * aren't interpreted as one feature and merged.
     */
    public List<Object> getForeignIdValues(Object source) {        
        if (foreignIds != null) {
            List<Object> foreignIdValues = new ArrayList<Object>();
            for (int i = 0; i<foreignIds.size(); i++) {
                foreignIdValues.add(i, peekValue(source, foreignIds.get(i)));
            }
            return foreignIdValues;
        }
        return null;
    }
    
    /**
     * Only used for Joining, to make sure that rows with different foreign id's
     * aren't interpreted as one feature and merged.
     */
    protected boolean checkForeignIdValues(List<Object> foreignIdValues, Feature next) {        
        if (foreignIds!=null) {
            for (int i = 0; i < foreignIds.size(); i++) {
                if (!peekValue(next, foreignIds.get(i)).toString().equals(foreignIdValues.get(i).toString())) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Only used for Joining, to make sure that rows with different foreign id's
     * aren't interpreted as one feature and merged.
     */
    public List<Object> getIdValues(Object source) {   
        List<Object> ids = new ArrayList<Object>();
        FilterAttributeExtractor extractor = new FilterAttributeExtractor();
        mapping.getFeatureIdExpression().accept(extractor, null);
        for (String att : extractor.getAttributeNameSet()) {
            ids.add(peekValue(source, namespaceAwareFilterFactory.property( att)));
        }
        
        if (foreignIds != null) {
            ids.addAll(getForeignIdValues(source));
        }
        return ids;
    }
        
    /**
     * Only used for Joining, to make sure that rows with different foreign id's
     * aren't interpreted as one feature and merged.
     */
    public boolean checkForeignIdValues(List<Object> foreignIdValues) {        
        return checkForeignIdValues(foreignIdValues, curSrcFeature);
    }

    protected void initialiseSourceFeatures(FeatureTypeMapping mapping, Query query,
            CoordinateReferenceSystem targetCRS) throws IOException {
        mappedSource = mapping.getSource();

        //NC - joining query
        if (query instanceof JoiningQuery) {
            if (mappedSource instanceof JDBCFeatureSource) {
                mappedSource = new JoiningJDBCFeatureSource((JDBCFeatureSource) mappedSource);
            } else if (mappedSource instanceof JDBCFeatureStore) {
                mappedSource = new JoiningJDBCFeatureSource((JDBCFeatureStore) mappedSource);
            } else {
                throw new IllegalArgumentException("Joining queries are only supported on JDBC data stores");
            }

        }
        String version=(String)this.mapping.getTargetFeature().getType().getUserData().get("targetVersion");
        //might be because top level feature has no geometry
        if (targetCRS == null && version!=null) {
            // figure out the crs the data is in
            CoordinateReferenceSystem crs=null;
            try{
                crs = this.mappedSource.getSchema().getCoordinateReferenceSystem();
            }catch(UnsupportedOperationException e){
                //do nothing as mappedSource is a WSFeatureSource
            }
            // gather declared CRS
            CoordinateReferenceSystem declaredCRS = this.getDeclaredCrs(crs, version);
            CoordinateReferenceSystem target;
            Object crsobject = this.mapping.getTargetFeature().getType().getUserData().get("targetCrs");
            if (crsobject instanceof CoordinateReferenceSystem) {
            	target = (CoordinateReferenceSystem) crsobject;
            } else if (crsobject instanceof URI) {
            
	            URI uri=(URI) crsobject;
	            if (uri != null) {
	                try {
	                    target = CRS.decode(uri.toString());
	                } catch (Exception e) {
	                    String msg = "Unable to support srsName: " + uri;
	                    throw new UnsupportedOperationException(msg, e);
	                }
	            } else {
	                target = declaredCRS;
	            }
            } else {
            	target = declaredCRS;
            }
            this.reprojection = target;
            
        } else {
            this.reprojection = targetCRS;
        }
        
        //clean up user data related to request
        mapping.getTargetFeature().getType().getUserData().put("targetVersion", null);
        mapping.getTargetFeature().getType().getUserData().put("targetCrs", null);
        
        //reproject target feature
        targetFeature = reprojectAttribute(mapping.getTargetFeature());

        // we need to disable the max number of features retrieved so we can
        // sort them manually just in case the data is denormalised
        query.setMaxFeatures(Query.DEFAULT_MAX);
        sourceFeatures = mappedSource.getFeatures(query);
        if (reprojection != null) {
            xpathAttributeBuilder.setCRS(reprojection);
            if (sourceFeatures.getSchema().getGeometryDescriptor() == null
                    || this.isReprojectionCrsEqual(this.mappedSource.getSchema()
                            .getCoordinateReferenceSystem(), this.reprojection)) {
                // VT: No point trying to re-project without any geometry.
                query.setCoordinateSystemReproject(null);
            }
        }
        if (!(this instanceof XmlMappingFeatureIterator)) {
            this.sourceFeatureIterator = sourceFeatures.features();
        }

        // NC - joining nested atts
        for (AttributeMapping attMapping : selectedMapping) {

            if (attMapping instanceof JoiningNestedAttributeMapping) {
                ((JoiningNestedAttributeMapping) attMapping).open(this, query);

            }

        }

    }
    
//    public boolean isExistingNestedFeatures() {
//    	return this.isExistingIterator;
//    }

    public boolean unprocessedFeatureExists() {

        boolean exists = getSourceFeatureIterator().hasNext();
        if (exists && this.curSrcFeature == null) {
            this.curSrcFeature = getSourceFeatureIterator().next();
        }

        return exists;
    }

    protected String extractIdForFeature(Feature feature) {
        if (mapping.getFeatureIdExpression().equals(Expression.NIL)) {
            if (feature.getIdentifier() == null) {
                return null;
            } else {
                return feature.getIdentifier().getID();
            }
        }
        return mapping.getFeatureIdExpression().evaluate(feature, String.class);
    }

    protected String extractIdForAttribute(final Expression idExpression, Object sourceInstance) {
        String value = (String) idExpression.evaluate(sourceInstance, String.class);
        return value;
    }

    protected boolean isNextSourceFeatureNull() {
        return curSrcFeature == null;
    }

    protected boolean sourceFeatureIteratorHasNext() {
        return getSourceFeatureIterator().hasNext();
    }

    protected Object getValues(boolean isMultiValued, Expression expression,
            Object sourceFeatureInput) {
        if (isMultiValued && sourceFeatureInput instanceof FeatureImpl
                && expression instanceof AttributeExpressionImpl) {
            // RA: Feature Chaining
            // complex features can have multiple nodes of the same attribute.. and if they are used
            // as input to an app-schema data access to be nested inside another feature type of a
            // different XML type, it has to be mapped like this:
            // <AttributeMapping>
            // <targetAttribute>
            // gsml:composition
            // </targetAttribute>
            // <sourceExpression>
            // <inputAttribute>mo:composition</inputAttribute>
            // <linkElement>gsml:CompositionPart</linkElement>
            // <linkField>gml:name</linkField>
            // </sourceExpression>
            // <isMultiple>true</isMultiple>
            // </AttributeMapping>
            // As there can be multiple nodes of mo:composition in this case, we need to retrieve
            // all of them
            AttributeExpressionImpl attribExpression = ((AttributeExpressionImpl) expression);
            String xpath = attribExpression.getPropertyName();
            ComplexAttribute sourceFeature = (ComplexAttribute) sourceFeatureInput;
            StepList xpathSteps = XPath.steps(sourceFeature.getDescriptor(), xpath, namespaces);
            return getProperties(sourceFeature, xpathSteps);
        }
        return expression.evaluate(sourceFeatureInput);
    }

    /**
     * Sets the values of grouping attributes.
     *
     * @param target
     * @param source
     * @param attMapping
     * @param values
     *
     * @return Feature. Target feature sets with simple attributes
     */
    protected Attribute setAttributeValue(Attribute target, String id, final Object source,
            final AttributeMapping attMapping, Object values, StepList inputXpath, List<PropertyName> selectedProperties) throws IOException {

        final Expression sourceExpression = attMapping.getSourceExpression();
        final AttributeType targetNodeType = attMapping.getTargetNodeInstance();
        StepList xpath = inputXpath == null ? attMapping.getTargetXPath().clone() : inputXpath;

        Map<Name, Expression> clientPropsMappings = attMapping.getClientProperties();
        boolean isNestedFeature = attMapping.isNestedAttribute();
        
        if (id == null && Expression.NIL != attMapping.getIdentifierExpression()) {
            id = extractIdForAttribute(attMapping.getIdentifierExpression(), source);
        }

        Attribute instance = null;
        
        if (attMapping.isNestedAttribute()) {
            NestedAttributeMapping nestedMapping = ((NestedAttributeMapping) attMapping);
            Object mappingName = nestedMapping.getNestedFeatureType(source);
            if (mappingName != null) {
                if (nestedMapping.isSameSource() && mappingName instanceof Name) {
                    // data type polymorphism mapping
                    return setPolymorphicValues((Name) mappingName, target, id, nestedMapping, source,
                            xpath, clientPropsMappings);
                } else if (mappingName instanceof String) {
                    // referential polymorphism mapping
                    return setPolymorphicReference((String) mappingName, clientPropsMappings, target,
                            xpath, targetNodeType, attMapping.getDescriptors());
                }
            } else {
                // polymorphism could result in null, to skip the attribute
                return null;
            }
        }
        if (values == null && source != null) {
            values = getValues(attMapping.isMultiValued(), sourceExpression, source);
        }
        
		AttributeDescriptor attDescriptor = attMapping.getDescriptors()
				.get(0);
		
        if (isNestedFeature) {
        	if (values == null) {
                // polymorphism use case, if the value doesn't match anything, don't encode
                return null;
            }
            boolean isHRefLink = isByReference(clientPropsMappings, isNestedFeature);
        	Iterator<? extends Attribute> nestedFeatures = null;
			List<Object> foreignIds = getIdValues(source);
 
			// get built feature based on link value
            if (values instanceof Collection) {
                    if (isHRefLink) {
                        // get the input features to avoid infinite loop in case the nested
                        // feature type also have a reference back to this type
                        // eg. gsml:GeologicUnit/gsml:occurence/gsml:MappedFeature
                        // and gsml:MappedFeature/gsml:specification/gsml:GeologicUnit
                    	nestedFeatures = ((NestedAttributeMapping) attMapping)
                                .getInputFeatures(this, values, foreignIds, source, reprojection, selectedProperties, includeMandatory);
                    } else {
                    	nestedFeatures = ((NestedAttributeMapping) attMapping).getFeatures(
                                this, values, foreignIds, reprojection, source, selectedProperties, includeMandatory);
                    }
            } else if (isHRefLink) {
                // get the input features to avoid infinite loop in case the nested
                // feature type also have a reference back to this type
                // eg. gsml:GeologicUnit/gsml:occurence/gsml:MappedFeature
                // and gsml:MappedFeature/gsml:specification/gsml:GeologicUnit
            	nestedFeatures = ((NestedAttributeMapping) attMapping).getInputFeatures(this, values, foreignIds, source, reprojection, selectedProperties, includeMandatory);
            } else {
            	nestedFeatures = ((NestedAttributeMapping) attMapping).getFeatures(this, values, foreignIds, reprojection,
                        source, selectedProperties, includeMandatory);
            }
            // exclude geometries?
            
            if (nestedFeatures instanceof DataAccessMappingFeatureIterator) {
            	if (!((DataAccessMappingFeatureIterator)nestedFeatures).isAvailable()) {
            		// the iterator has already been closed, no more features
            		return null;
            	}
            }
            if (attMapping instanceof JoiningNestedAttributeMapping) {

				List<Property> propsBefore = new ArrayList<Property>();
			
				// check for existing attribute and group it
				// TODO: check for null, check for ComplexAttribute
				Attribute nestedFeaturesElement;

				boolean isNewAttribute = false;
				
				if (target instanceof ComplexAttribute) {
					Collection<Property> existingProp = ((ComplexAttribute) target)
							.getProperties(attDescriptor.getName());
					if (!existingProp.isEmpty() 
							&& (attDescriptor.getMaxOccurs() == 1)) { 
//							|| ((DataAccessMappingFeatureIterator)nestedFeatures).isExistingNestedFeatures())) {
							// if nested features is a different iterator than existing one - for polymorphism,
							// add it to the attribute
							// else the foreign id values would've been added in the
							// iterator, so no need to create another attribute
							return null;
					}
					NestedAttributeImpl nestedAttContainer = null;
					for (Property prop : existingProp) {
						if (prop instanceof NestedAttributeImpl) {
							nestedAttContainer = (NestedAttributeImpl) prop;
						} else {
							propsBefore.add(prop);
						}
					}
					if (Types.equals(attDescriptor.getType().getName(), XS.ANYTYPE)) {
						nestedFeaturesElement = builder.addComplexAnyTypeAttribute(null, attDescriptor, id);
					} else {
					    nestedFeaturesElement = builder.add(id,
							null, null, attDescriptor,
							this.reprojection);
					}
					setClientProperties(nestedFeaturesElement, source,
							clientPropsMappings);
					if (attMapping.encodeIfEmpty()) {
						nestedFeaturesElement.getDescriptor().getUserData()
								.put("encodeIfEmpty", true);
					}
					if (propsBefore.isEmpty()) {
						// no existing nested features of this element
						// create one
						if (nestedAttContainer == null) {
							if (isHRefLink) {
								nestedAttContainer = this.attf
										.createNestedAttribute(
												attDescriptor,
												id,
												(XlinkSourceIterator) nestedFeatures,
												nestedFeaturesElement,
												foreignIds, clientPropsMappings
														.get(XLINK_HREF_NAME));
							} else {
								nestedAttContainer = this.attf
										.createNestedAttribute(
												attDescriptor,
												id,
												(DataAccessMappingFeatureIterator) nestedFeatures,
												nestedFeaturesElement,
												foreignIds);
							}
							isNewAttribute = true;
						} else {
							if (isHRefLink) {
								((NestedAttributeImpl) nestedAttContainer)
										.addIterator(
												(XlinkSourceIterator) nestedFeatures,
												nestedFeaturesElement,
												clientPropsMappings
														.get(XLINK_HREF_NAME));
							} else {
								((NestedAttributeImpl) nestedAttContainer)
										.addIterator(
												(DataAccessMappingFeatureIterator) nestedFeatures,
												nestedFeaturesElement);
							}
						}
					}  else if (nestedAttContainer == null) {
						nestedAttContainer = this.attf.createNestedAttribute(attDescriptor, id, (Attribute) propsBefore.get(0), foreignIds);
							// maintain the order of mappings
						for (int i = 1; i < propsBefore.size(); i++) {
							nestedAttContainer.add(propsBefore.get(i));
					    } 		
						if (isHRefLink) {
							((NestedAttributeImpl) nestedAttContainer).addIterator(
									(XlinkSourceIterator)nestedFeatures,
									nestedFeaturesElement, clientPropsMappings.get(XLINK_HREF_NAME));
						} else {
							((NestedAttributeImpl) nestedAttContainer).addIterator(
									(DataAccessMappingFeatureIterator)nestedFeatures,
									nestedFeaturesElement);
						}
					} else {
						if (isHRefLink) {
							((NestedAttributeImpl) nestedAttContainer).addIterator(
									(XlinkSourceIterator)nestedFeatures,
									nestedFeaturesElement, clientPropsMappings.get(XLINK_HREF_NAME));
						} else {
							((NestedAttributeImpl) nestedAttContainer).addIterator(
									(DataAccessMappingFeatureIterator)nestedFeatures,
									nestedFeaturesElement);
						}
					}
					instance = nestedAttContainer;
				}

				if (attDescriptor.getMaxOccurs() == 1) {
					// single value, evaluate now so the encoder can encode
					// correctly, since it evaluates maxOccurs
					// use while so it would close itself when it reaches the end
					// single value, evaluate now so the encoder can encode
					// correctly, since it evaluates maxOccurs
					// use while so it would close itself when it reaches the end
					
					if (instance instanceof NestedAttributeImpl) {
						Iterator<Property> iterator = ((NestedAttributeImpl) instance).iterator();
						if (iterator.hasNext()) {
							instance = (Attribute) iterator.next();
						} else {
							return null;
						}
					}
				}
				if (instance != null) {
					XSDElementDeclaration elemDecl = (XSDElementDeclaration) attDescriptor
							.getUserData().get(XSDElementDeclaration.class);
					instance.getUserData().put(XSDElementDeclaration.class,
							elemDecl);
					// so it won't get skipped in cleanEmptyElements()
					instance.getDescriptor().getUserData()
							.put("encodeIfEmpty", true);
					
					if (instance.getDescriptor() instanceof GeometryDescriptor) {
						System.out.println("");
					}
					
					List newValue = new ArrayList();	
					Object existingValue = target.getValue();				
					if (existingValue != null) {

						Collection<Property> existingProps = (Collection) existingValue;

						if (!propsBefore.isEmpty()) {
							// remove properties from target that were merged
							// into NestedAttributeImpl
							for (Property prop : existingProps) {
								if (!propsBefore.contains(prop)) {
									newValue.add(prop);
								}
							}
							newValue.add(instance);
						} else {
							// add all existing values
							newValue.addAll(existingProps);
							if (isNewAttribute) {
								newValue.add(instance);
							}
						}
					}

					target.setValue(newValue);
				}
		} 
        }
        if (instance == null && !(attMapping instanceof JoiningNestedAttributeMapping)) {
			if (values instanceof Attribute) {
				// copy client properties from input features if they're complex
				// features
				// wrapped in app-schema data access
				Map<Name, Expression> newClientProps = getClientProperties((Attribute) values);
				if (!newClientProps.isEmpty()) {
					newClientProps.putAll(clientPropsMappings);
					clientPropsMappings = newClientProps;
				}
				values = ((Attribute) values).getValue();
			}
			instance = xpathAttributeBuilder.set(target, xpath, values, id,
					targetNodeType, false, sourceExpression,
					attMapping.getDescriptors());
			setClientProperties(instance, source, clientPropsMappings);
			if (instance != null && attMapping.encodeIfEmpty()) {
				instance.getDescriptor().getUserData()
						.put("encodeIfEmpty", true);
			}
			if (target instanceof ComplexAttribute) {
				// check for existing properties of nested features
				// and group this together
				Collection<Property> existingProps = ((ComplexAttribute) target)
						.getProperties(attDescriptor.getName());
				boolean propExists = false;
				for (Property prop : existingProps) {
					if (prop instanceof NestedAttributeImpl) {
						NestedAttributeImpl nestedAttContainer = (NestedAttributeImpl) prop;
						nestedAttContainer.add(instance);
						propExists = true;
						break;
					}
				}
				// remove this instance from the target
				if (propExists) {
					List newValue = new ArrayList();
					for (Property prop : existingProps) {
						if (!prop.equals(instance)) {
							newValue.add(prop);
						}
					}
					target.setValue(newValue);
				}
			}
		}
        
        return instance;
    }

    /**
     * Special handling for polymorphic mapping where the value of the attribute determines that
     * this attribute should be a placeholder for an xlink:href.
     *
     * @param uri
     *            the xlink:href URI
     * @param clientPropsMappings
     *            client properties
     * @param target
     *            the complex feature being built
     * @param xpath
     *            the xpath of attribute
     * @param targetNodeType
     *            the type of the attribute to be cast to, if any
     */
    private Attribute setPolymorphicReference(String uri,
            Map<Name, Expression> clientPropsMappings, Attribute target, StepList xpath,
            AttributeType targetNodeType, List<AttributeDescriptor> descriptors) {
        
        if (uri != null) {
            Attribute instance = xpathAttributeBuilder.set(target, xpath, null, "", targetNodeType,
                    true, null, descriptors);
            Map<Name, Expression> newClientProps = new HashMap<Name, Expression>();
            newClientProps.putAll(clientPropsMappings);
            newClientProps.put(XLINK_HREF_NAME, namespaceAwareFilterFactory.literal(uri));
            setClientProperties(instance, null, newClientProps);
            return instance;
        }
        return null;
    }

    /**
     * Special handling for polymorphic mapping. Works out the polymorphic type name by evaluating
     * the function on the feature, then set the relevant sub-type values.
     *
     * @param target
     *            The target feature to be encoded
     * @param id
     *            The target feature id
     * @param nestedMapping
     *            The mapping that is polymorphic
     * @param source
     *            The source simple feature
     * @param xpath
     *            The xpath of polymorphic type
     * @param clientPropsMappings
     *            Client properties
     * @throws IOException
     */
    private Attribute setPolymorphicValues(Name mappingName, Attribute target, String id,
            NestedAttributeMapping nestedMapping, Object source, StepList xpath,
            Map<Name, Expression> clientPropsMappings) throws IOException {
        // process sub-type mapping
        DataAccess<FeatureType, Feature> da = DataAccessRegistry.getDataAccess((Name) mappingName);
        if (da instanceof AppSchemaDataAccess) {
            // why wouldn't it be? check just to be safe
            FeatureTypeMapping fTypeMapping = ((AppSchemaDataAccess) da)
                    .getMappingByName((Name) mappingName);
            List<AttributeMapping> polymorphicMappings = fTypeMapping.getAttributeMappings();
            AttributeDescriptor attDescriptor = fTypeMapping.getTargetFeature();
            AttributeType type = attDescriptor.getType();
            Name polymorphicTypeName = attDescriptor.getName();
            StepList prefixedXpath = xpath.clone();
            prefixedXpath.add(new Step(new QName(polymorphicTypeName.getNamespaceURI(),
                    polymorphicTypeName.getLocalPart(), this.namespaces
                            .getPrefix(polymorphicTypeName.getNamespaceURI())), 1));
            if (!fTypeMapping.getFeatureIdExpression().equals (Expression.NIL)) {
                id = fTypeMapping.getFeatureIdExpression().evaluate(source, String.class);
            }
            List<AttributeDescriptor> descriptors = new ArrayList<AttributeDescriptor>();
            descriptors.addAll(nestedMapping.getDescriptors());
            descriptors.add(attDescriptor);
            Attribute instance = xpathAttributeBuilder.set(target, prefixedXpath, null, id,
                    type, false, null, descriptors);
            if (instance != null) {
            setClientProperties(instance, source, clientPropsMappings);
            for (AttributeMapping mapping : polymorphicMappings) {
                if (skipTopElement(polymorphicTypeName, mapping, type)) {
                    // if the top level mapping for the Feature itself, the attribute instance
                    // has already been created.. just need to set the client properties
                    setClientProperties(instance, source, mapping.getClientProperties());
                    continue;
                }
                setAttributeValue(instance, null, source, mapping, null, null, selectedProperties.get(mapping));
            }
            }
            return instance;
        }
        return null;
    }

    /**
     * Set xlink:href client property for multi-valued chained features. This has to be specially
     * handled because we don't want to encode the nested features attributes, since it's already an
     * xLink. Also we need to eliminate duplicates.
     *
     * @param target
     *            The target attribute
     * @param clientPropsMappings
     *            Client properties mappings
     * @param nestedFeatures
     *            Nested features
     * @param xpath
     *            Attribute xPath where the client properties are to be set
     * @param targetNodeType
     *            Target node type
     */
    protected void setXlinkReference(Attribute target, Map<Name, Expression> clientPropsMappings,
            Iterator<? extends Attribute> nestedFeatures, StepList xpath, AttributeType targetNodeType, List<AttributeDescriptor> descriptors) {
        if (nestedFeatures == null) {
        	return;
        }
    	Expression linkExpression = clientPropsMappings.get(XLINK_HREF_NAME);
        while (nestedFeatures.hasNext()) {
        	Attribute singleVal = nestedFeatures.next();
        
            // Make sure the same value isn't already set
            // in case it comes from a denormalized view for many-to-many relationship.
            // (1) Get the first existing value
            Collection<Property> existingAttributes = null;
            
            if (target instanceof ComplexAttribute) {
            	existingAttributes = getProperties((ComplexAttribute) target, xpath);
            }
            boolean exists = false;

            if (existingAttributes != null) {
                for (Property existingAttribute : existingAttributes) {
                    Object existingValue = existingAttribute.getUserData().get(Attributes.class);
                    if (existingValue != null) {
                        assert existingValue instanceof HashMap;
                        existingValue = ((Map) existingValue).get(XLINK_HREF_NAME);
                    }
                    if (existingValue != null) {
                        Object hrefValue = linkExpression.evaluate(singleVal);
                        if (hrefValue != null && hrefValue.equals(existingValue)) {
                            // (2) if one of the new values matches the first existing value,
                            // that means this comes from a denormalized view,
                            // and this set has already been set
                            exists = true;
                            // stop looking once found
                            break;
                        }
                    }
                }
            }
            if (!exists) {
                Attribute instance = xpathAttributeBuilder.set(target, xpath, null, null,
                        targetNodeType, true, null, descriptors);
                setClientProperties(instance, singleVal, clientPropsMappings);
            }
        }
    }

//    protected List<Feature> setNextFeature(String fId, List<Object> foreignIdValues) throws IOException {
//        List<Feature> features = new ArrayList<Feature>();
//        features.add(curSrcFeature);        
//        curSrcFeature = null;
//
//        while (getSourceFeatureIterator().hasNext()) {
//            Feature next = getSourceFeatureIterator().next();
//            if (extractIdForFeature(next).equals(fId) && checkForeignIdValues(foreignIdValues, next)) {
//                // HACK HACK HACK
//                // evaluate filter that applies to this list as we want a subset
//                // instead of full result
//                // this is a temporary solution for Bureau of Meteorology
//                // requirement for timePositionList
//                if (listFilter != null) {
//                    if (listFilter.evaluate(next)) {
//                        features.add(next);
//                    }
//                // END OF HACK
//                } else {
//                    features.add(next);
//                }
//            // HACK HACK HACK
//            // evaluate filter that applies to this list as we want a subset
//            // instead of full result
//            // this is a temporary solution for Bureau of Meteorology
//            // requirement for timePositionList
//            } else if (listFilter == null || listFilter.evaluate(next)) {
//            // END OF HACK
//                curSrcFeature = next;
//                break;
//            }
//        }
//        return features;
//    }
    
    /**
     * Only used when joining is not used and pre-filter exists because the sources will match
     * the prefilter but there might be denormalised rows with same id that don't.
     * @param fId
     * @param features
     * @throws IOException
     */
    protected FeatureIterator<? extends Feature> getFilteredSources(String fId) throws IOException {
        FeatureCollection<? extends FeatureType, ? extends Feature> matchingFeatures;
        Query query = new Query();
        if (reprojection != null) {
            if (sourceFeatures.getSchema().getGeometryDescriptor() != null
                    && !this.isReprojectionCrsEqual(this.mappedSource.getSchema()
                            .getCoordinateReferenceSystem(), this.reprojection)) {
                query.setCoordinateSystemReproject(reprojection);
            }
        }
        
        Filter fidFilter;

        if (mapping.getFeatureIdExpression().equals(Expression.NIL)) {
            // no real feature id mapping,
            // so let's find by database row id
            Set<FeatureId> ids = new HashSet<FeatureId>();
            FeatureId featureId = namespaceAwareFilterFactory.featureId(fId);
            ids.add(featureId);
            fidFilter = namespaceAwareFilterFactory.id(ids);
        } else {
            // in case the expression is wrapped in a function, eg. strConcat
            // that's why we don't always filter by id, but do a PropertyIsEqualTo
            fidFilter = namespaceAwareFilterFactory.equals(mapping.getFeatureIdExpression(),
                    namespaceAwareFilterFactory.literal(fId));
        }
        
        // HACK HACK HACK
        // evaluate filter that applies to this list as we want a subset
        // instead of full result
        // this is a temporary solution for Bureau of Meteorology
        // requirement for timePositionList
        if (listFilter != null) {
            List<Filter> filters = new ArrayList<Filter>();
            filters.add(listFilter);
            filters.add(fidFilter);
            fidFilter = namespaceAwareFilterFactory.and(filters);
        }
        // END OF HACK

        query.setFilter(fidFilter);
        matchingFeatures = this.mappedSource.getFeatures(query);
        
        FeatureIterator<? extends Feature> iterator = matchingFeatures.features();

        if (filteredFeatures != null) {
            filteredFeatures.add(fId);
        }

        return iterator;
    }

	public void skipNestedMapping(AttributeMapping attMapping, Feature source)
			throws IOException {
		if (attMapping instanceof JoiningNestedAttributeMapping) {

			Object value = getValues(attMapping.isMultiValued(),
					attMapping.getSourceExpression(), source);

			if (value instanceof Collection) {
				for (Object val : (Collection) value) {
					((JoiningNestedAttributeMapping) attMapping).skip(this,
							val, getIdValues(source));
				}
			} else {
				((JoiningNestedAttributeMapping) attMapping).skip(this, value,
						getIdValues(source));
			}

		}
	}

	public void skip() throws IOException {
		this.setNextSrc(true);
		
		for (AttributeMapping attMapping : selectedMapping) {
			skipNestedMapping(attMapping, curSrcFeature);
		}
		
		// Also skip rows with same id as they would form 1 complex feature
		FeatureIterator sources;
		
		String id = getNextFeatureId();
		
		if (isFiltered) {
			sources = getFilteredSources(id);
		} else {
			sources = getSourceFeatureIterator();
		}
		
		Feature source;

		boolean nextIdFound = false;
		
		while (sources.hasNext()) {
			source = sources.next();

			if (!isFiltered) {
				if (!(extractIdForFeature(source).equals(id) 
						&& checkForeignIdValues(getForeignIdValues(curSrcFeature), source))) {
					curSrcFeature = source;
					nextIdFound = true;
					break;
				}
			} 
			
			for (AttributeMapping attMapping : selectedMapping) {
				skipNestedMapping(attMapping, source);
			}
		}
		
		if (!nextIdFound) {
		this.curSrcFeature = null;
		}

	}
    
    public Feature getNextXlinkSource() throws IOException {
        
//        String id = extractIdForFeature(curSrcFeature);
        
//        FeatureIterator sources = getSourceFeatureIterator();
//        
//		Feature source;
//		if (sources.hasNext()) {
			Feature source;
			
			if (curSrcFeature != null) {
				source = curSrcFeature;
				curSrcFeature = null;
			} else {
  			    source = getSourceFeatureIterator().next();
			}
//			if (extractIdForFeature(source).equals(id)
//					&& checkForeignIdValues(groupingForeignIds, source)) {
//				break;
//			}
//			for (AttributeMapping attMapping : selectedMapping) {
//				skipNestedMapping(attMapping, source);
//			}

	        setNextSrc(true);
	        
			return source;
//		}
		
        
    }
    
    private GeometryDescriptor reprojectGeometry(GeometryDescriptor descr) {
    	if (descr == null) {
    		return null;
    	}
    	GeometryType type = ftf.createGeometryType(descr.getType().getName(), descr.getType().getBinding(), reprojection, descr.getType().isIdentified(), descr.getType().isAbstract(), descr.getType().getRestrictions(), descr.getType().getSuper(), descr.getType().getDescription());
    	type.getUserData().putAll(descr.getType().getUserData());
    	GeometryDescriptor gd = ftf.createGeometryDescriptor(type, descr.getName(), descr.getMinOccurs(), descr.getMaxOccurs(), descr.isNillable(), descr.getDefaultValue());
    	gd.getUserData().putAll(descr.getUserData());
    	return gd;
    }

    private FeatureType reprojectType(FeatureType type) {
    	Collection<PropertyDescriptor> schema = new ArrayList<PropertyDescriptor>();
    	
    	for (PropertyDescriptor descr : type.getDescriptors()) {
    		if (descr instanceof GeometryDescriptor) {
    			schema.add(reprojectGeometry((GeometryDescriptor)descr));    			
    			}
    		else {
    			schema.add(descr);
    		}
    	}

    	FeatureType ft;
    	if (type instanceof NonFeatureTypeProxy) {
    	    ft = new NonFeatureTypeProxy(((NonFeatureTypeProxy) type).getSubject(), mapping, schema);
    	} else {
    	    ft = ftf.createFeatureType(type.getName(), schema, reprojectGeometry(type.getGeometryDescriptor()), type.isAbstract(), type.getRestrictions(), type.getSuper(), type.getDescription());
    	}
    	ft.getUserData().putAll(type.getUserData());
    	return ft;
    }

    
    private AttributeDescriptor reprojectAttribute(AttributeDescriptor descr) {
    	
    	if ( reprojection != null && descr.getType() instanceof FeatureType ) {    	
    		AttributeDescriptor ad = ftf.createAttributeDescriptor(reprojectType((FeatureType) descr.getType()), descr.getName(), descr.getMinOccurs(), descr.getMaxOccurs(), descr.isNillable(), descr.getDefaultValue());
    		ad.getUserData().putAll(descr.getUserData());
    		return ad;
    	} else {    		
    		return descr;
    	}
    }

	protected Feature computeNext() throws IOException {

		long start = System.currentTimeMillis();

		String id = getNextFeatureId();
		Feature target = attf.createFeature(null, targetFeature, id);
		FeatureIterator<? extends Feature> sources;

		if (isFiltered) {
			sources = getFilteredSources(id);
		} else {
			sources = getSourceFeatureIterator();
		}

		boolean nextIdFound = false;

		final Name targetNodeName = targetFeature.getName();
		Feature source = curSrcFeature;
		int index = 0;

		List<AttributeMapping> lastIndexMapping = null;

		while (source != null) {
			for (AttributeMapping attMapping : selectedMapping) {
				try {
					if (skipTopElement(targetNodeName, attMapping,
							targetFeature.getType())) {
						// ignore the top level mapping for the Feature itself
						// as it was already set
						continue;
					}
					if (attMapping.isList()) {
						Attribute instance;

						AttributeExpressionImpl exp = new AttributeExpressionImpl(
								attMapping.getTargetXPath().toString(),
								this.namespaces);
						Object existingAtt = exp.evaluate(target);
						if (existingAtt == null) {
							instance = setAttributeValue(target, null, source,
									attMapping, null, null,
									selectedProperties.get(attMapping));
						} else {
							instance = (Attribute) existingAtt;
							List<Object> values = new ArrayList<Object>();
							Object existingValues = instance.getValue();
							if (existingValues instanceof Collection) {
								values.addAll((Collection) existingValues);
							} else {
								values.add(existingValues);
							}
							Expression sourceExpr = attMapping
									.getSourceExpression();
							values.add(getValue(sourceExpr, source));
							String valueString = StringUtils.join(
									values.iterator(), " ");

							StepList fullPath = attMapping.getTargetXPath();
							StepList leafPath = fullPath.subList(
									fullPath.size() - 1, fullPath.size());

							if (instance instanceof ComplexAttributeImpl) {
								// xpath builder will work out the leaf
								// attribute to set values on
								xpathAttributeBuilder
										.set(instance, leafPath, valueString,
												null, null, false, sourceExpr,
												attMapping.getDescriptors());
							} else {
								// simple attributes
								instance.setValue(valueString);
							}
						}
					} else if (attMapping.isMultiValued()) {
						// extract the values from multiple source features of
						// the same id
						// and set them to one built feature
						setAttributeValue(target, null, source, attMapping,
								null, null, selectedProperties.get(attMapping));
					} else {
						String indexString = attMapping.getSourceIndex();
						// if not specified, get the first row by default
						if (indexString != null) {
							if (!ComplexFeatureConstants.LAST_INDEX
									.equals(indexString)) {
								if (lastIndexMapping == null) {
									lastIndexMapping = new ArrayList<AttributeMapping>();
								}
								lastIndexMapping.add(attMapping);
								break;
							}
						}
						if ((indexString != null && Integer.parseInt(indexString) == index)
								|| (indexString == null && index == 0)) {
							setAttributeValue(target, null, source, attMapping,
									null, null,
									selectedProperties.get(attMapping));
							// When a feature is not multi-valued but still has
							// multiple rows with the same ID in
							// a denormalised table, by default app-schema only
							// takes the first row and ignores
							// the rest (see above). The following line is to
							// make sure that the cursors in the
							// 'joining nested mappings'skip any extra rows that
							// were linked to those rows that are being ignored.
							// Otherwise the cursor will stay there in the wrong
							// spot and none of the following feature chaining
							// will work. That can really only occur if the
							// foreign key is not unique for the ID of the
							// parent
							// feature (otherwise all of those rows would be
							// already passed when creating the feature based on
							// the first row). This never really occurs in
							// practice I have noticed, but it is a theoretic
							// possibility, as there is no requirement for the
							// foreign key to be unique per id.
							if (index > 0) {
							    skipNestedMapping(attMapping, source);
							}
						}
					}
				} catch (Exception e) {
					throw new RuntimeException(
							"Error applying mapping with targetAttribute "
									+ attMapping.getTargetXPath(), e);
				}
			}
			source = null;
			
			if (sources.hasNext()) {
				source = sources.next();

				if (!isFiltered) {
					if (!(extractIdForFeature(source).equals(id) && checkForeignIdValues(
							getForeignIdValues(curSrcFeature), source))) {

						if (lastIndexMapping != null) {
							// set last index
							for (AttributeMapping att : lastIndexMapping) {
								setAttributeValue(target, null, source, att,
										null, null, selectedProperties.get(att));
								// When a feature is not multi-valued but still
								// has multiple rows with the same ID in
								// a denormalised table, by default app-schema
								// only takes the first row and ignores
								// the rest (see above). The following line is
								// to make sure that the cursors in the
								// 'joining nested mappings'skip any extra rows
								// that were linked to those rows that are being
								// ignored.
								// Otherwise the cursor will stay there in the
								// wrong spot and none of the following feature
								// chaining
								// will work. That can really only occur if the
								// foreign key is not unique for the ID of the
								// parent
								// feature (otherwise all of those rows would be
								// already passed when creating the feature
								// based on
								// the first row). This never really occurs in
								// practice I have noticed, but it is a
								// theoretic
								// possibility, as there is no requirement for
								// the foreign key to be unique per id.
								if (index > 0) {
								    skipNestedMapping(att, source);
								}
							}
						}
						curSrcFeature = source;
						nextIdFound = true;
						break;
					}
				}
			}
			index++;
		}

		long end = System.currentTimeMillis();
		//
		millis += (end - start);

		start = System.currentTimeMillis();
		cleanEmptyElements(target);
		cleanmillis += System.currentTimeMillis() - start;

		if (!nextIdFound) {
			curSrcFeature = null;
		}

		return target;
	}
//    
//	private void buildFeature(Feature target, Feature source, int count) throws IOException {
//		for (AttributeMapping attMapping : selectedMapping) {
//		try {
//			if (attMapping.isMultiValued()) {
//			    setAttributeValue(target, null, source, attMapping, null, null,
//					selectedProperties.get(attMapping));
//			} else if (attMapping.isList()) {
//                setListAttributes(target, sources, attMapping);
//			} else {
//				// When a feature is not multi-valued but still has multiple
//				// rows
//				// with the same ID in
//				// a denormalised table, by default app-schema only takes the
//				// first
//				// row and ignores
//				// the rest (see above). The following line is to make sure that
//				// the
//				// cursors in the
//				// 'joining nested mappings'skip any extra rows that were linked
//				// to
//				// those rows that are being ignored.
//				// Otherwise the cursor will stay there in the wrong spot and
//				// none
//				// of the following feature chaining
//				// will work. That can really only occur if the foreign key is
//				// not
//				// unique for the ID of the parent
//				// feature (otherwise all of those rows would be already passed
//				// when
//				// creating the feature based on
//				// the first row). This never really occurs in practice I have
//				// noticed, but it is a theoretic
//				// possibility, as there is no requirement for the foreign key
//				// to be
//				// unique per id.
//				String indexString = attMapping.getSourceIndex();
//				if (indexString == null) {
//					if (count == 0) {
//					// first row
//						setAttributeValue(target, null, source, attMapping, null, null,
//								selectedProperties.get(attMapping));
//					}	
//				} else if (!ComplexFeatureConstants.LAST_INDEX.equals(indexString)
//		        	&& count == Integer.parseInt(indexString)) {
//					setAttributeValue(target, null, source, attMapping, null, null,
//							selectedProperties.get(attMapping));
//		        }
//			}
//			// When a feature is not multi-valued but still has multiple
//			// rows with the same ID in
//			// a denormalised table, by default app-schema only takes the
//			// first row and ignores
//			// the rest (see above). The following line is to make sure that
//			// the cursors in the
//			// 'joining nested mappings'skip any extra rows that were linked
//			// to those rows that are being ignored.
//			// Otherwise the cursor will stay there in the wrong spot and
//			// none of the following feature chaining
//			// will work. That can really only occur if the foreign key is
//			// not unique for the ID of the parent
//			// feature (otherwise all of those rows would be already passed
//			// when creating the feature based on
//			// the first row). This never really occurs in practice I have
//			// noticed, but it is a theoretic
//			// possibility, as there is no requirement for the foreign key
//			// to be unique per id.
////			if (index > 0) {
////				skipNestedMapping(attMapping, source);
////			}
//		} catch (Exception e) {
//			throw new RuntimeException(
//					"Error applying mapping with targetAttribute "
//							+ attMapping.getTargetXPath(), e);
//		}
//		
//				
//				
////				if (count > 0) {
////					skipNestedMapping(attMapping, source);
//////				}
////			} catch (Exception e) {
////				throw new RuntimeException(
////						"Error applying mapping with targetAttribute "
////								+ attMapping.getTargetXPath(), e);
////			}
//		}
//	}

	private List<Feature> getSources(String id) {
		// TODO Auto-generated method stub
		return null;
	}

	protected void setListAttributes(Feature target, List<Feature> sources, AttributeMapping attMapping) {
			try {
				Attribute instance = setAttributeValue(target, null,
						sources.get(0), attMapping, null, null,
						selectedProperties.get(attMapping));
				if (sources.size() > 1 && instance != null) {
					List<Object> values = new ArrayList<Object>();
					Expression sourceExpr = attMapping.getSourceExpression();
					for (Feature source : sources) {
						values.add(getValue(sourceExpr, source));
					}
					String valueString = StringUtils.join(values.iterator(),
							" ");
					StepList fullPath = attMapping.getTargetXPath();
					StepList leafPath = fullPath.subList(fullPath.size() - 1,
							fullPath.size());
					if (instance instanceof ComplexAttributeImpl) {
						// xpath builder will work out the leaf attribute to set
						// values on
						xpathAttributeBuilder.set(instance, leafPath,
								valueString, null, null, false, sourceExpr,
								attMapping.getDescriptors());
					} else {
						// simple attributes
						instance.setValue(valueString);
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(
						"Error applying mapping with targetAttribute "
								+ attMapping.getTargetXPath(), e);
			}
	}
	
//	protected void setMultiValuedAttributes(Feature target, Feature source)
//			throws IOException {
////		for (AttributeMapping attMapping : multiValuedMapping) {
//			try {
//				setAttributeValue(target, null, source, attMapping, null, null,
//						selectedProperties.get(attMapping));
//				// When a feature is not multi-valued but still has multiple
//				// rows with the same ID in
//				// a denormalised table, by default app-schema only takes the
//				// first row and ignores
//				// the rest (see above). The following line is to make sure that
//				// the cursors in the
//				// 'joining nested mappings'skip any extra rows that were linked
//				// to those rows that are being ignored.
//				// Otherwise the cursor will stay there in the wrong spot and
//				// none of the following feature chaining
//				// will work. That can really only occur if the foreign key is
//				// not unique for the ID of the parent
//				// feature (otherwise all of those rows would be already passed
//				// when creating the feature based on
//				// the first row). This never really occurs in practice I have
//				// noticed, but it is a theoretic
//				// possibility, as there is no requirement for the foreign key
//				// to be unique per id.
////				if (index > 0) {
////					skipNestedMapping(attMapping, source);
////				}
//			} catch (Exception e) {
//				throw new RuntimeException(
//						"Error applying mapping with targetAttribute "
//								+ attMapping.getTargetXPath(), e);
//			}
//		}
//	}

    /**
     * Get all source features of the provided id. This assumes the source features are grouped by
     * id.
     * 
     * @param id
     *            The feature id
     * @return list of source features
     * @throws IOException
     */
//    protected FeatureIterator<Feature> getSources(String id) throws IOException {  
//		if (isFiltered) {
//			return getFilteredSources();
//		} else {
//			return getGroupedSources(id, getForeignIdValues(curSrcFeature));
//		}
//    }
        
    protected String getNextFeatureId() {
        return extractIdForFeature(curSrcFeature);
    }

    protected void cleanEmptyElements(Feature target) throws DataSourceException {
        try {
            ArrayList values = new ArrayList<Property>();
            for (Iterator i = target.getValue().iterator(); i.hasNext();) {
                Property p = (Property) i.next();
                if (p.getDescriptor().getMinOccurs() > 0 || getEncodeIfEmpty(p) || hasChild(p)) {
                    values.add(p);
                }
            }
            target.setValue(values);
        } catch (DataSourceException e) {
            throw new DataSourceException("Unable to clean empty element", e);
        }
    }
    
    private boolean hasChild(Property p) throws DataSourceException {
        boolean result = false;
        if (p.getValue() instanceof Collection) {

            Collection c = (Collection) p.getValue();
            
            if (this.getClientProperties(p).containsKey(XLINK_HREF_NAME)) {
                return true;
            }
            
            ArrayList values = new ArrayList();
            for (Object o : c) {
                if (o instanceof Property) {
                    if (hasChild((Property) o)) {
                        values.add(o);
                        result = true;
                    } else if (getEncodeIfEmpty((Property) o)) {
                        values.add(o);
                        result = true;
                    } else if (((Property) o).getDescriptor().getMinOccurs() > 0) {
                        if (((Property) o).getDescriptor().isNillable()) {
                            // add nil mandatory property
                            values.add(o);
                        }
                    }
                } 
            }
            p.setValue(values);
        } else if (p.getName().equals(ComplexFeatureConstants.FEATURE_CHAINING_LINK_NAME)) {
            // ignore fake attribute FEATURE_LINK
            result = false;
        } else if (p.getValue() != null && p.getValue().toString().length() > 0) {
            result = true;
        }
        return result;
    }

    protected Feature populateFeatureData(String id) throws IOException {
        throw new UnsupportedOperationException("populateFeatureData should not be called!");
    }
    
//    protected void clean() {
//    	// release resources without closing joining nested attribute mapping because we still need them
//    	// for other features
//        if (sourceFeatures != null && getSourceFeatureIterator() != null) {
//            sourceFeatureIterator.close();
//            sourceFeatureIterator = null;
//            sourceFeatures = null;
//            filteredFeatures = null;
//            listFilter = null;
//            groupingForeignIds = null;
//            
//
//    		System.out.println(String.format("Building %d features in app-schema took: %d millis", 
//    				featureCounter,
//            	    TimeUnit.MILLISECONDS.toMillis(millis)
//            	));
//    		
//    		System.out.println(String.format("Cleaning empty elements for %d features in app-schema took: %d millis", 
//    				featureCounter,
//            	    TimeUnit.MILLISECONDS.toMillis(cleanmillis)
//            	));
//        }
//    }
    
    @Override
	public void close() {
    	closeSourceFeatures();
    	// only close nested features at the end because they may still be needed
    	closeNestedFeatures();
    }
    
    private void closeNestedFeatures() {
    	//NC - joining nested atts
		if (selectedMapping != null) {
			for (AttributeMapping attMapping : selectedMapping) {
				if (attMapping instanceof JoiningNestedAttributeMapping) {
					((JoiningNestedAttributeMapping) attMapping).close(this);
				}
			}

			this.selectedMapping = null;
		}
    }    

    protected void closeSourceFeatures() {
        if (sourceFeatures != null && getSourceFeatureIterator() != null) {
            sourceFeatureIterator.close();
            curSrcFeature = null;
            sourceFeatureIterator = null;
            sourceFeatures = null;
            filteredFeatures = null;
            listFilter = null;
            this.attf = null;
            this.builder = null;
            this.foreignIds = null;
            this.ftf = null;
            this.includeMandatory = false;
            this.isBuildingXlinkHref = false;
            this.isFiltered = false;
            this.mappedSource = null;
            this.mapping = null;
            this.namespaceAwareFilterFactory = null;
            this.namespaces = null;
            this.query = null;
            this.reprojection = null;  
            this.selectedProperties = null;
            this.targetFeature = null;  
            this.skipCounter = -1;
//            this.selectedMapping = null;
//            groupingForeignIds = null;        
            
    		System.out.println(String.format("Building %d features in app-schema took: %d millis", 
    				featureCounter,
            	    TimeUnit.MILLISECONDS.toMillis(millis)
            	));
        }        
    }

    protected Object getValue(final Expression expression, Object sourceFeature) {
        Object value = expression.evaluate(sourceFeature);
        if (value instanceof Attribute) {
            value = ((Attribute) value).getValue();
        }
        return value;
    }

    /**
     * Returns first matching attribute from provided root and xPath.
     *
     * @param root
     *            The root attribute to start searching from
     * @param xpath
     *            The xPath matching the attribute
     * @return The first matching attribute
     */
//    private Property getProperty(Attribute root, StepList xpath) {
//        Property property = root;
//
//        final StepList steps = new StepList(xpath);
//
//        Iterator<Step> stepsIterator = steps.iterator();
//
//        while (stepsIterator.hasNext()) {
//            assert property instanceof ComplexAttribute;
//            Step step = stepsIterator.next();
//            property = ((ComplexAttribute) property).getProperty(Types.toTypeName(step.getName()));
//            if (property == null) {
//                return null;
//            }
//        }
//        return property;
//    }

    /**
     * Return all matching properties from provided root attribute and xPath.
     *
     * @param root
     *            The root attribute to start searching from
     * @param xpath
     *            The xPath matching the attribute
     * @return The matching attributes collection
     */
    private Collection<Property> getProperties(ComplexAttribute root, StepList xpath) {

        final StepList steps = new StepList(xpath);

        Iterator<Step> stepsIterator = steps.iterator();
        Collection<Property> properties = null;
        Step step = null;
        if (stepsIterator.hasNext()) {
            step = stepsIterator.next();
            properties = ((ComplexAttribute) root).getProperties(Types.toTypeName(step.getName()));
        }

        while (stepsIterator.hasNext()) {
            step = stepsIterator.next();
            Collection<Property> nestedProperties = new ArrayList<Property>();
            for (Property property : properties) {
                assert property instanceof ComplexAttribute;
                Collection<Property> tempProperties = ((ComplexAttribute) property)
                        .getProperties(Types.toTypeName(step.getName()));
                if (!tempProperties.isEmpty()) {
                    nestedProperties.addAll(tempProperties);
                }
            }
            properties.clear();
            if (nestedProperties.isEmpty()) {
                return properties;
            }
            properties.addAll(nestedProperties);
        }
        return properties;
    }

    /**
     * Checks if client property has xlink:ref in it, if the attribute is for chained features.
     *
     * @param clientPropsMappings
     *            the client properties mappings
     * @param isNested
     *            true if we're dealing with chained/nested features
     * @return
     */
    protected boolean isByReference(Map<Name, Expression> clientPropsMappings, boolean isNested) {
        // only care for chained features
        return isNested ? (clientPropsMappings.isEmpty() ? false : (clientPropsMappings
                .get(XLINK_HREF_NAME) == null) ? false : true) : false;
    }
    
    /**
     * Returns the declared CRS given the native CRS and the request WFS version
     * 
     * @param nativeCRS
     * @param wfsVersion
     * @return
     */
    private CoordinateReferenceSystem getDeclaredCrs(CoordinateReferenceSystem nativeCRS,
            String wfsVersion) {
        try {
            if(nativeCRS == null)
                return null;
            
            if (wfsVersion.equals("1.0.0")) {
                return nativeCRS;
            } else {
                String code = GML2EncodingUtils.epsgCode(nativeCRS);
                //it's possible that we can't do the CRS -> code -> CRS conversion...so we'll just return what we have
                if (code == null) return nativeCRS;
                return CRS.decode("urn:x-ogc:def:crs:EPSG:6.11.2:" + code);
            }
        } catch (Exception e) {
            throw new UnsupportedOperationException("We have had issues trying to flip axis of " + nativeCRS, e);
        }
    }
    
    public boolean isReprojectionCrsEqual(CoordinateReferenceSystem source,CoordinateReferenceSystem target) {
        return CRS.equalsIgnoreMetadata(source,target);
    }
    
    public void setListFilter(Filter filter) {
        listFilter = filter;
    } 
    
    
    private boolean getEncodeIfEmpty(Property p) {
        Object o = ((p.getDescriptor()).getUserData().get("encodeIfEmpty"));
        if (o == null) {
            return false;
        }
        return (Boolean) o;
    }
    
//    public void setExistingIteratorFlag(boolean b) {
//    	this.isExistingIterator = b;
//    }

//	public void setGroupingForeignIds(List<Object> idValues) {
////		if (this.groupingForeignIds != null) {
////			for (Object id : idValues) {
////				if (!groupingForeignIds.contains(id)) {
////					groupingForeignIds.add(id);
////				}
////			}
////			isExistingIterator = true;
////		} else {
//   		    this.groupingForeignIds = idValues;
////		}
//	}

	public void setXlinkHrefMode(boolean b) {
		this.isBuildingXlinkHref = b;
	}
//	public void setInstance(Instance instance) {
//		this.instance = instance;
//	}

	public void addSkippedCount() {
		if (skipCounter < 0) {
			throw new IllegalArgumentException("Trying to modify skipCount to closed iterator!");
		}
		skipCounter++;
	}

	public void removeSkippedCount() {
		if (skipCounter >= 0) {
			skipCounter--;
		}		
	}

	public int getSkippedCount() {
		// TODO Auto-generated method stub
		return skipCounter;
	}

	public CoordinateReferenceSystem getReprojection() {
		return this.reprojection;
	}

	public boolean getIncludeMandatory() {
		// TODO Auto-generated method stub
		return this.includeMandatory;
	}

	public Map<AttributeMapping, List<PropertyName>> getSelectedProperties() {
		return this.selectedProperties;
	}

	public Query getQuery() {
		return query;
	}

	public Name getMappingName() {
		// TODO Auto-generated method stub
		return this.mapping.getMappingName();
	}

//	public void reset() throws IOException {
//		Query unrolledQuery = getUnrolledQuery(query);
//        initialiseSourceFeatures(mapping, unrolledQuery, query.getCoordinateSystemReproject());
//        setNextSrc(true);
//	}
}
