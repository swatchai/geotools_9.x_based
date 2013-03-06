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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.geotools.data.complex.config.FeatureTypeRegistry;
import org.geotools.data.complex.config.NonFeatureTypeProxy;
import org.geotools.data.complex.filter.XPath;
import org.geotools.data.complex.filter.XPath.Step;
import org.geotools.data.complex.filter.XPath.StepList;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.Types;
import org.geotools.feature.type.AttributeDescriptorImpl;
import org.geotools.feature.type.ComplexFeatureTypeFactoryImpl;
import org.geotools.feature.type.GeometryTypeImpl;
import org.geotools.feature.type.UniqueNameFeatureTypeFactoryImpl;
import org.geotools.util.Utilities;
import org.geotools.xs.XS;
import org.geotools.xs.XSSchema;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.ComplexType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.expression.Expression;
import com.vividsolutions.jts.geom.Geometry;

/**
 * @author Gabriel Roldan (Axios Engineering)
 * @author Rini Angreani (CSIRO Earth Science and Resource Engineering)
 * @version $Id$
 *
 *
 *
 * @source $URL$
 * @since 2.4
 */
public class AttributeMapping {
    /** Expression to set the Attribute's ID from, or {@linkplain Expression#NIL} */
    private Expression identifierExpression;

    protected Expression sourceExpression;

    protected StepList targetXPath;

    private boolean isMultiValued;
    
    private boolean encodeIfEmpty;
    
    private boolean isList;

    /**
     * If present, represents our way to deal polymorphic attribute instances, so this node should
     * be of a subtype of the one referenced by {@link  #targetXPath}
     */
    AttributeType targetNodeInstance;

    private Map<Name, Expression> clientProperties;

    private String label;

    private String parentLabel;

    private String instancePath;

    private String sourceIndex;

	private List<AttributeDescriptor> descriptors;
    /**
     * Creates a new AttributeMapping object.
     * 
     * @param sourceExpression
     *                DOCUMENT ME!
     * @param targetXPath
     *                DOCUMENT ME!
     */
    public AttributeMapping(Expression idExpression, Expression sourceExpression,
            StepList targetXPath) {
        this(idExpression, sourceExpression, null, targetXPath, null, false, null);
    }

    public AttributeMapping(Expression idExpression, Expression sourceExpression, String sourceIndex,
            StepList targetXPath, AttributeType targetNodeInstance, boolean isMultiValued,
            Map<Name, Expression> clientProperties) {

        this.identifierExpression = idExpression == null ? Expression.NIL : idExpression;
        this.sourceExpression = sourceExpression == null ? Expression.NIL : sourceExpression;
        this.isMultiValued = isMultiValued;
        if (this.sourceExpression == null) {
            this.sourceExpression = Expression.NIL;
        }
        this.sourceIndex = sourceIndex;
        this.targetXPath = targetXPath;
        this.targetNodeInstance = targetNodeInstance;
        this.clientProperties = clientProperties == null ? Collections
                .<Name, Expression> emptyMap() : clientProperties;

    }

    public boolean isMultiValued() {
        return isMultiValued;
    }
    
    public boolean encodeIfEmpty() {
        return encodeIfEmpty;
    }
    
    public boolean isList() {
        return isList;
    }

    public Expression getSourceExpression() {
        return sourceExpression;
    }
    
    public String getSourceIndex() {
        return sourceIndex;
    }

    public StepList getTargetXPath() {
        return targetXPath;
    }

    public AttributeType getTargetNodeInstance() {
        return targetNodeInstance;
    }

    /**
     * This is overridden by NestedAttributeMapping
     * 
     * @return always return false
     */
    public boolean isNestedAttribute() {
        return false;
    }   
    
    /**********************************************************************
     * Label, parentLabel and instancePath are for web service backend only
     **********************************************************************/
    public String getLabel() {
        return label;
    }

    public String getParentLabel() {
        return parentLabel;
    }  
    
    public String getInstanceXpath() {
        return instancePath;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }

    public void setParentLabel(String label) {
        parentLabel = label;
    }  
    
    public void setInstanceXpath(String instancePath) {
        this.instancePath = instancePath;
    }    
    
    public void setList(boolean isList) {
        this.isList = isList;
    }
    
    /********END specific web service methods*******************/

    public void setEncodeIfEmpty(boolean encodeIfEmpty) {
        this.encodeIfEmpty = encodeIfEmpty;
    }
    
    /**
     * Get base (non-collection) type of simple content.
     * 
     * @param type
     * @return
     */
    static AttributeType getSimpleContentType(AttributeType type) {
        Class<?> binding = type.getBinding();
        if (binding == Collection.class) {
            return getSimpleContentType(type.getSuper());
        } else {
            return type;
        }
    }
    
	public void setDescriptors(AttributeDescriptor parentDescriptor,
			UniqueNameFeatureTypeFactoryImpl descriptorFactory,
			Map<Name, AttributeType> elemToTargetNodeType) {
		descriptors = new ArrayList<AttributeDescriptor>();

		if (Types.equals(ComplexFeatureConstants.FEATURE_CHAINING_LINK_NAME,
				targetXPath.get(0).getName())) {
			descriptors.add(ComplexFeatureConstants.FEATURE_CHAINING_LINK);
			return;
		}

		Name rootName = parentDescriptor.getName();
		Step rootStep = targetXPath.get(0);
		if (Types.equals(rootName, rootStep.getName())) {
			// first step is the self reference to att, so skip it
			if (targetXPath.size() > 1) {
				targetXPath.remove(0);
			} else {
				// except when the xpath is the root itself
				// where it is done for feature chaining for simple content
				if (Types.isSimpleContentType(parentDescriptor.getType())) {
					AttributeType simpleContentType = getSimpleContentType(parentDescriptor
							.getType());
					descriptors.add(new AttributeDescriptorImpl(
							simpleContentType,
							ComplexFeatureConstants.SIMPLE_CONTENT, 1, 1, true,
							(Object) null));
				} else if (Types.isGeometryType(parentDescriptor.getType())) {
					ComplexFeatureTypeFactoryImpl typeFactory = new ComplexFeatureTypeFactoryImpl();
					GeometryType geomType;
					if (parentDescriptor.getType() instanceof GeometryType) {
						geomType = (GeometryType) parentDescriptor.getType();
					} else {
						geomType = (GeometryType) ((NonFeatureTypeProxy) parentDescriptor
								.getType()).getSubject();
					}
					GeometryDescriptor geomDescriptor = typeFactory
							.createGeometryDescriptor(geomType, rootName,
									parentDescriptor.getMinOccurs(),
									parentDescriptor.getMaxOccurs(),
									parentDescriptor.isNillable(),
									parentDescriptor.getDefaultValue());
					descriptors.add(geomDescriptor);
				}
				return;
			}
		}

		Iterator stepsIterator = targetXPath.iterator();

		AttributeDescriptor currStepDescriptor = null;

		int stepIndex = 0;
		while (stepsIterator.hasNext()) {
			final XPath.Step currStep = (Step) stepsIterator.next();
			final boolean isLastStep = !stepsIterator.hasNext();
			final QName stepName = currStep.getName();
			final Name attributeName = Types.toName(stepName);

			final AttributeType _parentType = parentDescriptor.getType();

			ComplexType parentType = (ComplexType) _parentType;

			AttributeDescriptor actualDescriptor;
			if (!isLastStep) {
				if (null == attributeName.getNamespaceURI()) {
					actualDescriptor = (AttributeDescriptor) Types
							.findDescriptor(parentType,
									attributeName.getLocalPart());
				} else {
					actualDescriptor = (AttributeDescriptor) Types
							.findDescriptor(parentType, attributeName);
				}
				
				if (actualDescriptor == null) {
					// might be a type cast with targetAttributeNode
					// find the castType from previous attribute mappings
					// and override it
					AttributeType castType = elemToTargetNodeType
							.get(currStepDescriptor.getName());
					
					if (castType != null && castType instanceof ComplexType) {
						parentType = (ComplexType) castType;
						if (null == attributeName.getNamespaceURI()) {
							actualDescriptor = (AttributeDescriptor) Types
									.findDescriptor(parentType,
											attributeName.getLocalPart());
						} else {
							actualDescriptor = (AttributeDescriptor) Types
									.findDescriptor(parentType, attributeName);
						}
					}
				}
				
				if (actualDescriptor != null
						&& Types.equals(actualDescriptor.getType().getName(),
						XS.ANYTYPE)) {
					// check for any type
					// find the castType from previous attribute mappings
					// and override it
					AttributeType castType = elemToTargetNodeType
							.get(actualDescriptor.getName());
					currStepDescriptor = descriptorFactory
							.createAttributeDescriptor(castType, attributeName,
									actualDescriptor.getMinOccurs(),
									actualDescriptor.getMaxOccurs(),
									actualDescriptor.isNillable(), null);
				} else {
					currStepDescriptor = actualDescriptor;
				}
		    
			} else {
				if (null == attributeName.getNamespaceURI()) {
					actualDescriptor = (AttributeDescriptor) Types
							.findDescriptor(parentType,
									attributeName.getLocalPart());
				} else {
					actualDescriptor = (AttributeDescriptor) Types
							.findDescriptor(parentType, attributeName);
				}

				if (targetNodeInstance == null) {
					currStepDescriptor = actualDescriptor;
				} else if (actualDescriptor != null) {
					int minOccurs = actualDescriptor.getMinOccurs();
					int maxOccurs = actualDescriptor.getMaxOccurs();
					boolean nillable = actualDescriptor.isNillable();
					if (actualDescriptor instanceof GeometryDescriptor) {
						// important to maintain CRS information encoding
						if (Geometry.class.isAssignableFrom(targetNodeInstance
								.getBinding())) {
							if (!(targetNodeInstance instanceof GeometryType)) {
								targetNodeInstance = new GeometryTypeImpl(
										targetNodeInstance.getName(),
										targetNodeInstance.getBinding(),
										((GeometryDescriptor) actualDescriptor)
												.getCoordinateReferenceSystem(),
										targetNodeInstance.isIdentified(),
										targetNodeInstance.isAbstract(),
										targetNodeInstance.getRestrictions(),
										targetNodeInstance.getSuper(),
										targetNodeInstance.getDescription());
							}
							currStepDescriptor = descriptorFactory
									.createGeometryDescriptor(
											(GeometryType) targetNodeInstance,
											attributeName, minOccurs,
											maxOccurs, nillable, null);
						} else {
							throw new IllegalArgumentException(
									"Can't set targetNodeType: "
											+ targetNodeInstance.toString()
											+ " for attribute mapping: "
											+ attributeName
											+ " as it is not a Geometry type!");
						}
					} else {
						currStepDescriptor = descriptorFactory
								.createAttributeDescriptor(targetNodeInstance,
										attributeName, minOccurs, maxOccurs,
										nillable, null);
					}
				}
				
			}
			if (currStepDescriptor == null) {

				if (isLastStep) {
					// reached the leaf
					throw new IllegalArgumentException(currStep
							+ " is not a valid location path for type "
							+ _parentType.getName());
				}
				StringBuffer parentAtts = new StringBuffer();
				Collection properties = parentType.getDescriptors();
				for (Iterator it = properties.iterator(); it.hasNext();) {
					PropertyDescriptor desc = (PropertyDescriptor) it
							.next();
					Name name = desc.getName();
					parentAtts.append(name.getNamespaceURI());
					parentAtts.append("#");
					parentAtts.append(name.getLocalPart());
					if (it.hasNext()) {
						parentAtts.append(", ");
					}
				}
				throw new IllegalArgumentException(currStep
						+ " is not a valid location path for type "
						+ _parentType.getName() + ". " + currStep + " ns: "
						+ currStep.getName().getNamespaceURI() + ", "
						+ _parentType.getName().getLocalPart()
						+ " properties: " + parentAtts);
			}

			descriptors.add(currStepDescriptor);
			parentDescriptor = currStepDescriptor;
			stepIndex++;
		}

	}
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof AttributeMapping)) {
            return false;
        }

        AttributeMapping other = (AttributeMapping) o;

        return Utilities.equals(identifierExpression, other.identifierExpression)
                && Utilities.equals(sourceExpression, other.sourceExpression)
                && Utilities.equals(targetXPath, other.targetXPath)
                && Utilities.equals(targetNodeInstance, other.targetNodeInstance)
                && Utilities.equals(isList, other.isList)
                && Utilities.equals(isMultiValued, other.isMultiValued)
                && Utilities.equals(clientProperties, other.clientProperties)
                && Utilities.equals(label, other.label)
                && Utilities.equals(parentLabel, other.parentLabel);
    }

    public int hashCode() {
        return (37 * identifierExpression.hashCode() + 37 * sourceExpression.hashCode())
                ^ targetXPath.hashCode();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("AttributeMapping[");
        sb.append("sourceExpression='").append(sourceExpression).append("', targetXPath='").append(
                targetXPath);
        if (targetNodeInstance != null) {
            sb.append(", target instance type=").append(targetNodeInstance);
        }
        sb.append("']");

        return sb.toString();
    }

    public Map<Name, Expression> getClientProperties() {
        return clientProperties == null ? Collections.<Name, Expression> emptyMap()
                : clientProperties;
    }

    public Expression getIdentifierExpression() {
        return identifierExpression;
    }

    public void setIdentifierExpression(Expression identifierExpression) {
        this.identifierExpression = identifierExpression;
    }
    
    public List<AttributeDescriptor> getDescriptors() {
    	return this.descriptors;
    }

}
