/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2005-2011, Open Source Geospatial Foundation (OSGeo)
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

package org.geotools.data.complex.filter;

import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

import org.geotools.data.complex.ComplexFeatureConstants;
import org.geotools.data.complex.config.NonFeatureTypeProxy;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.feature.AttributeBuilder;
import org.geotools.feature.AttributeImpl;
import org.geotools.feature.ComplexAttributeImpl;
import org.geotools.feature.GeometryAttributeImpl;
import org.geotools.feature.Types;
import org.geotools.feature.ValidatingFeatureFactoryImpl;
import org.geotools.feature.type.AttributeDescriptorImpl;
import org.geotools.feature.type.ComplexFeatureTypeFactoryImpl;
import org.geotools.feature.type.GeometryTypeImpl;
import org.geotools.feature.type.UniqueNameFeatureTypeFactoryImpl;
import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.expression.FeaturePropertyAccessorFactory;
import org.geotools.gml3.GML;
import org.geotools.util.CheckedArrayList;
import org.geotools.xs.XSSchema;
import org.opengis.feature.Attribute;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureFactory;
import org.opengis.feature.Property;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.ComplexType;
import org.opengis.feature.type.FeatureTypeFactory;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.PropertyName;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.util.Cloneable;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.NamespaceSupport;

import com.vividsolutions.jts.geom.Geometry;

/**
 * Utility class to evaluate XPath expressions against an Attribute instance, which may be any
 * Attribute, whether it is simple, complex, a feature, etc.
 * <p>
 * At the difference of the Filter subsystem, which works against Attribute contents (for example to
 * evaluate a comparison filter), the XPath subsystem, for which this class is the single entry
 * point, works against Attribute instances. That is, the result of an XPath expression, if a single
 * value, is an Attribtue, not the attribute content, or a List of Attributes, for instance.
 * </p>
 * 
 * @author Gabriel Roldan (Axios Engineering)
 * @author Rini Angreani (CSIRO Earth Science and Resource Engineering)
 * @version $Id$
 *
 *
 *
 * @source $URL$
 *         http://svn.osgeo.org/geotools/trunk/modules/unsupported/app-schema/app-schema/src/main
 *         /java/org/geotools/data/complex/filter/XPath.java $
 * @since 2.4
 */
public class XPath {

    private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger(XPath.class
            .getPackage().getName());

    private FilterFactory FF;

    private FeatureFactory featureFactory;
    
    private CoordinateReferenceSystem crs;

    /**
     * Used to create specific attribute descriptors for
     * {@link #set(Attribute, String, Object, String, AttributeType)} when the actual attribute
     * instance is of a derived type of the corresponding one declared in the feature type.
     */
    private FeatureTypeFactory descriptorFactory;

	private NamespaceSupport ns;

	private AttributeBuilder builder;

    public XPath(AttributeBuilder builder) {
        this.builder = builder;
        this.FF = CommonFactoryFinder.getFilterFactory(null);
        this.featureFactory = new ValidatingFeatureFactoryImpl();
        this.descriptorFactory = new UniqueNameFeatureTypeFactoryImpl();
    }
    
    public XPath() {
        this.FF = CommonFactoryFinder.getFilterFactory(null);
        this.featureFactory = new ValidatingFeatureFactoryImpl();
        this.descriptorFactory = new UniqueNameFeatureTypeFactoryImpl();
        this.builder = new AttributeBuilder(featureFactory);
    }

    public XPath(FilterFactory ff, FeatureFactory featureFactory) {
        setFilterFactory(ff);
        setFeatureFactory(featureFactory);
        this.builder = new AttributeBuilder(featureFactory);
        // this.descriptorFactory = new TypeFactoryImpl();
    }

    public void setFilterFactory(FilterFactory ff) {
        this.FF = ff;
    }
    
    public void setCRS(CoordinateReferenceSystem crs) {
        this.crs = crs;
    }

    public void setFeatureFactory(FeatureFactory featureFactory) {
        this.featureFactory = featureFactory;
    }

    public static class StepList extends CheckedArrayList<Step> {
        private static final long serialVersionUID = -5612786286175355862L;

        private StepList() {
            super(XPath.Step.class);
        }

        public StepList(StepList steps) {
            super(XPath.Step.class);
            addAll(steps);
        }

        public String toString() {
            StringBuffer sb = new StringBuffer();
            for (Iterator<Step> it = iterator(); it.hasNext();) {
                Step s = (Step) it.next();
                sb.append(s.toString());
                if (it.hasNext()) {
                    sb.append("/");
                }
            }
            return sb.toString();
        }
        
        public boolean containsPredicate() {
            for (int i=0; i< size(); i++) {
                if (get(i).getPredicate() != null) {
                    return true;
                }
            }
            
            return false;
        }
        
        public boolean startsWith(StepList other) {
            if (other.size() > this.size()) {
                return false;
            }
            for (int i = 0; i < other.size(); i++) {
                if (!this.get(i).equalsIgnoreIndex(other.get(i))) {
                    return false;
                }
            }
            return true;
        }

        public StepList subList(int fromIndex, int toIndex) {
            if (fromIndex < 0)
                throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
            if (toIndex > size())
                throw new IndexOutOfBoundsException("toIndex = " + toIndex);
            if (fromIndex > toIndex)
                throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex("
                        + toIndex + ")");
            StepList subList = new StepList();
            for (int i = fromIndex; i < toIndex; i++) {
                subList.add(this.get(i));
            }
            return subList;
        }

        public StepList clone() {
            StepList copy = new StepList();
            for (Step step : this) {
                copy.add((Step) step.clone());
            }
            return copy;
        }

        /**
         * Compares this StepList with another for equivalence regardless of the indexes of each
         * Step.
         * 
         * @param propertyName
         * @return <code>true</code> if this step list has the same location paths than
         *         <code>propertyName</code> ignoring the indexes in each step. <code>false</code>
         *         otherwise.
         */
        public boolean equalsIgnoreIndex(final StepList propertyName) {
            if (propertyName == null) {
                return false;
            }
            if (propertyName == this) {
                return true;
            }
            if (size() != propertyName.size()) {
                return false;
            }
            Iterator mine = iterator();
            Iterator him = propertyName.iterator();
            Step myStep;
            Step hisStep;
            while (mine.hasNext()) {
                myStep = (Step) mine.next();
                hisStep = (Step) him.next();
                if (!myStep.equalsIgnoreIndex(hisStep)) {
                    return false;
                }
            }
            return true;
        }
        
        /**
         * Find the first steps matching the xpath within this list, and set an index to it.
         * 
         * @param index
         *            the new index for the matching steps
         * @param xpath
         *            the xpath to be searched
         */
        public void setIndex(int index, String xpath, String separator) {
            if (this.toString().contains(xpath)) {
                for (int i = 0; i < size() - 1; i++) {
                    String firstString = get(i).toString();
                    if (xpath.equals(firstString)) {
                        get(i).setIndex(index);
                        return;
                    }
                    if (xpath.startsWith(firstString)) {
                        StringBuffer buf = new StringBuffer(firstString);
                        buf.append(separator);
                        for (int j = i + 1; j < size() - 1; j++) {
                            buf.append(get(j).toString());
                            if (buf.toString().equals(xpath)) {
                                get(j).setIndex(index);
                                return;
                            }
                            buf.append(separator);
                        }
                    }
                }
            }
        }
    }

    /**
     * 
     * @author Gabriel Roldan
     * 
     */
    public static class Step implements Cloneable {
        private int index;
        
        private String predicate =  null;

        private QName attributeName;

        private boolean isXmlAttribute;

        public boolean isIndexed;

        /**
         * Creates a "property" xpath step (i.e. isXmlAttribute() == false).
         * 
         * @param name
         * @param index
         */
        public Step(final QName name, final int index) {
            this(name, index, false, false);
        }

        /**
         * Creates an xpath step for the given qualified name and index; and the given flag to
         * indicate if it it an "attribute" or "property" step.
         * 
         * @param name
         *            the qualified name of the step (name should include prefix to be reflected in
         *            toString())
         * @param index
         *            the index (indexing starts at 1 for Xpath) of the step
         * @param isXmlAttribute
         *            whether the step referers to an "attribute" or a "property" (like for
         *            attributes and elements in xml)
         * @throws NullPointerException
         *             if <code>name==null</code>
         * @throws IllegalArgumentException
         *             if <code>index &lt; 1</code>
         */
        public Step(final QName name, final int index, boolean isXmlAttribute) {
            this(name, index, isXmlAttribute, false);
        }

        /**
         * Creates an xpath step for the given qualified name and index; and the given flag to
         * indicate if it it an "attribute" or "property" step.
         * 
         * @param name
         *            the qualified name of the step (name should include prefix to be reflected in
         *            toString())
         * @param index
         *            the index (indexing starts at 1 for Xpath) of the step
         * @param isXmlAttribute
         *            whether the step referers to an "attribute" or a "property" (like for
         *            attributes and elements in xml)
         * @param isIndexed
         *            whether or not the index is to be shown in the string representation even if
         *            index = 1
         * @throws NullPointerException
         *             if <code>name==null</code>
         * @throws IllegalArgumentException
         *             if <code>index &lt; 1</code>
         */
        public Step(final QName name, final int index, boolean isXmlAttribute, boolean isIndexed) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            if (index < 1) {
                throw new IllegalArgumentException("index shall be >= 1");
            }
            this.attributeName = name;
            this.index = index;
            this.isXmlAttribute = isXmlAttribute;
            this.isIndexed = isIndexed;
        }
        
        public Step(final QName name, boolean isXmlAttribute, final String predicate) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            this.attributeName = name;
            this.index = 1;
            this.isIndexed = false;
            this.isXmlAttribute = isXmlAttribute;
            this.predicate = predicate;
        }

        /**
         * Compares this Step with another for equivalence ignoring the steps indexes.
         * 
         * @param hisStep
         * @return
         */
        public boolean equalsIgnoreIndex(Step other) {
            if (other == null) {
                return false;
            }
            if (other == this) {
                return true;
            }
            return attributeName.equals(other.attributeName)
                    && isXmlAttribute == other.isXmlAttribute;
        }

        public int getIndex() {
            return index;
        }
        
        public String getPredicate() {
            return predicate;
        }

        public boolean isIndexed() {
            return isIndexed;
        }

        public QName getName() {
            return attributeName;
        }

        public String toString() {
            StringBuffer sb = new StringBuffer(isXmlAttribute ? "@" : "");
            if (XMLConstants.DEFAULT_NS_PREFIX != attributeName.getPrefix()) {
                sb.append(attributeName.getPrefix()).append(':');
            }
            sb.append(attributeName.getLocalPart());
            if (isIndexed) {
                // we want to print index = 1 as well if specified
                // so filtering on the first index doesn't return all
                // e.g. gml:name[1] doesn't get translated to
                // gml:name i.e. all gml:name instances
                sb.append("[").append(index).append("]");
            } else if (predicate != null) {
                sb.append("[").append(predicate).append("]");
            }
            return sb.toString();
        }

        public boolean equals(Object o) {
            if (!(o instanceof Step)) {
                return false;
            }
            Step s = (Step) o;
            return attributeName.equals(s.attributeName) && index == s.index
                    && isXmlAttribute == s.isXmlAttribute
                    && predicate == s.predicate;
        }

        public int hashCode() {
            return 17 * attributeName.hashCode() + 37 * index;
        }

        public Step clone() {
            return predicate==null?
                    new Step(this.attributeName, this.index, this.isXmlAttribute, this.isIndexed):
                    new Step(this.attributeName, this.isXmlAttribute, this.predicate);
        }

        /**
         * Flag that indicates that this single step refers to an "attribute" rather than a
         * "property".
         * <p>
         * I.e. it was created from the last step of an expression like
         * <code>foo/bar@attribute</code>.
         * </p>
         * 
         * @return
         */
        public boolean isXmlAttribute() {
            return isXmlAttribute;
        }
        
        /**
         * @return true if this step represents an id attribute
         */
        public boolean isId() {
            return isXmlAttribute && attributeName.equals(GML.id);
        }

        public void setIndex(int index) {
            this.index = index;
            isIndexed = true;
        }
    }
    
    /**
     * Split X-path string in to string steps, ignoring / characters inside [] 
     * 
     * @param s x-path string
     * @return list of string steps
     */
    private static List<String> splitPath(String s) {
        ArrayList<String> parts = new ArrayList<String>();
        
        StringBuffer b = new StringBuffer();
        int insideIndex = 0;
        for (int pos = 0 ; pos < s.length() ; pos++) {
            if (s.charAt(pos) == '/' && insideIndex==0) {
                parts.add(b.toString());
                b = new StringBuffer();
            } else {
                if (s.charAt(pos) == '[') {                
                    insideIndex++;
                } else if (s.charAt(pos) == ']') {
                    insideIndex--;
                }
                b.append(s.charAt(pos));
            }
        }
        parts.add(b.toString());
        return parts;
    }

    /**
     * Returns the list of steps in an x-path expression that represents the root element.
     * 
     * @param root
     *            non null descriptor of the root attribute, generally the Feature descriptor.
     * @param namespaces
     *            namespace support for generating qnames from namespaces.
     * @return A list of unique of steps in an xpath expression.
     * @throws IllegalArgumentException
     *             if <code>root</code> is undefined.
     */
    public static StepList rootElementSteps(final AttributeDescriptor rootElement,
            final NamespaceSupport namespaces) throws IllegalArgumentException {

        if (rootElement == null) {
            throw new NullPointerException("root");
        }
        StepList steps = new StepList();
        QName qName = Types.toQName(rootElement.getName(), namespaces);
        steps.add(new Step(qName, 1, false, false));
        return steps;
    }

    /**
     * Returns the list of stepts in <code>xpathExpression</code> by cleaning it up removing
     * unnecessary elements.
     * <p>
     * </p>
     * 
     * @param root
     *            non null descriptor of the root attribute, generally the Feature descriptor. Used
     *            to ignore the first step in xpathExpression if the expression's first step is
     *            named as rootName.
     * 
     * @param xpathExpression
     * @return
     * @throws IllegalArgumentException
     *             if <code>xpathExpression</code> has no steps or it isn't a valid XPath expression
     *             against <code>type</code>.
     */
    public static StepList steps(final AttributeDescriptor root, final String xpathExpression,
            final NamespaceSupport namespaces) throws IllegalArgumentException {

        if (root == null) {
            throw new NullPointerException("root");
        }

        if (xpathExpression == null) {
            throw new NullPointerException("xpathExpression");
        }

        String expression = xpathExpression.trim();

        if ("".equals(expression)) {
            throw new IllegalArgumentException("expression is empty");
        }

        StepList steps = new StepList();

        if ("/".equals(expression)) {
            expression = root.getName().getLocalPart();
        }

        if (expression.startsWith("/")) {
            expression = expression.substring(1);
        }

        final List<String> partialSteps = splitPath(expression);

        if (partialSteps.size() == 0) {
            throw new IllegalArgumentException("no steps provided");
        }

        int startIndex = 0;

        for (int i = startIndex; i < partialSteps.size(); i++) {

            String step = partialSteps.get(i);
            if ("..".equals(step)) {
                steps.remove(steps.size() - 1);
            } else if (".".equals(step)) {
                continue;
            } else {
                int index = 1;
                boolean isXmlAttribute = false;
                boolean isIndexed = false;
                String predicate = null;
                String stepName = step;
                if (step.indexOf('[') != -1) {
                    int start = step.indexOf('[');
                    int end = step.indexOf(']');
                    stepName = step.substring(0, start);
                    String s = step.substring(start + 1, end);
                    Scanner scanner = new Scanner(s);
                    if (scanner.hasNextInt()) {
                        index = scanner.nextInt();
                        isIndexed = true;
                    } else {
                        predicate = s;
                    }
                }
                if (step.charAt(0) == '@') {
                    isXmlAttribute = true;
                    stepName = stepName.substring(1);
                }
                QName qName = deglose(stepName, root, namespaces, isXmlAttribute);
                if (predicate == null) {
                    steps.add(new Step(qName, index, isXmlAttribute, isIndexed));
                } else {
                    steps.add(new Step(qName, isXmlAttribute, predicate));
                }
                 
            }
            //            
            // if (step.indexOf('[') != -1) {
            // int start = step.indexOf('[');
            // int end = step.indexOf(']');
            // String stepName = step.substring(0, start);
            // int stepIndex = Integer.parseInt(step.substring(start + 1, end));
            // QName qName = deglose(stepName, root, namespaces);
            // steps.add(new Step(qName, stepIndex));
            // } else if ("..".equals(step)) {
            // steps.remove(steps.size() - 1);
            // } else if (".".equals(step)) {
            // continue;
            // } else {
            // QName qName = deglose(step, root, namespaces);
            // steps.add(new Step(qName, 1));
            // }
        }

        // XPath simplification phase: if the xpath expression contains more
        // nodes
        // than the root node itself, and the root node is present, remove the
        // root
        // node as it is redundant
        if (root != null && steps.size() > 1) {
            Step step = (Step) steps.get(0);
            Name rootName = root.getName();
            QName stepName = step.getName();
            if (Types.equals(rootName, stepName)) {
                LOGGER.fine("removing root name from xpath " + steps + " as it is redundant");
                steps.remove(0);
            }
        }

        return steps;
    }

    private static QName deglose(final String prefixedName, final AttributeDescriptor root,
            final NamespaceSupport namespaces, boolean isXmlAttribute) {
        if (prefixedName == null) {
            throw new NullPointerException("prefixedName");
        }

        QName name = null;

        String prefix;
        final String namespaceUri;
        final String localName;

        int prefixIdx = prefixedName.indexOf(':');

        if (prefixIdx == -1) {
            localName = prefixedName;
            final Name rootName = root.getName();
            // don't use default namespace for client properties (xml attribute), and FEATURE_LINK
            final String defaultNamespace = (isXmlAttribute
                    || localName.equals(ComplexFeatureConstants.FEATURE_CHAINING_LINK_NAME
                            .getLocalPart()) || rootName.getNamespaceURI() == null) ? XMLConstants.NULL_NS_URI
                    : namespaces.getURI("") == null ? rootName.getNamespaceURI() : namespaces
                            .getURI("");
            namespaceUri = defaultNamespace;
            if (XMLConstants.NULL_NS_URI.equals(defaultNamespace)) {
                prefix = XMLConstants.DEFAULT_NS_PREFIX;
            } else {
                if (!localName.equals(rootName.getLocalPart())) {
                    LOGGER.warning("Using root's namespace " + defaultNamespace
                            + " for step named '" + localName + "', as no prefix was stated");
                }
                prefix = namespaces.getPrefix(defaultNamespace);

                if (prefix == null) {
                    //throw new IllegalStateException("Default namespace is not mapped to a prefix: "
                    //        + defaultNamespace);
                    prefix = "";
                }
            }
        } else {
            prefix = prefixedName.substring(0, prefixIdx);
            localName = prefixedName.substring(prefixIdx + 1);
            namespaceUri = namespaces.getURI(prefix);
        }

        name = new QName(namespaceUri, localName, prefix);

        return name;
    }

	public Attribute set(Attribute target, StepList xpath, Object userValue,
			String id, AttributeType targetNodeType, boolean isXlinkRef,
			Expression sourceExpression, List<AttributeDescriptor> descriptors) {
		if (XPath.LOGGER.isLoggable(Level.CONFIG)) {
			XPath.LOGGER.entering("XPath", "set", new Object[] { userValue, id,
					isXlinkRef, sourceExpression });
		}

		StepList steps = new StepList(xpath);
		Step rootStep = xpath.get(0);
		QName stepName = rootStep.getName();
		Attribute leafAttribute = null;
		AttributeDescriptor currDescriptor = null;
		if (Types.equals(target.getName(), stepName)) {
			// // first step is the self reference to att, so skip it
			if (steps.size() > 1) {
				steps.remove(0);
			} else {
				// except when the xpath is the root itself
				// where it is done for feature chaining for simple content
				if (Types.isSimpleContentType(target.getType())) {
					return setSimpleContentValue(target, userValue,
							descriptors.get(0));
				} else if (Types.isGeometryType(target.getType())) {
					AttributeDescriptor descriptor = descriptors.get(0);
					if (descriptor instanceof GeometryDescriptor) {
						GeometryAttributeImpl geom = new GeometryAttributeImpl(
								userValue, (GeometryDescriptor) descriptor,
								null);
						ArrayList<Property> geomAtts = new ArrayList<Property>();
						geomAtts.add(geom);
						target.setValue(geomAtts);
						return geom;
					} else {
						// throw exception
						return null;
					}
				}
			}
		}

		Object exValue = null;
		AttributeDescriptor descriptor;
		int stepIndex = steps.size() - 1;
		Attribute parent = target;
		while (leafAttribute == null && stepIndex > -1) {

			if (stepIndex == 0) {
				if (steps.size() > 1) {
					leafAttribute = createAttributeTree(0, parent, steps,
							descriptors, id, isXlinkRef, targetNodeType,
							userValue);
				} else {
					// set leaf attribute directly
					descriptor = reprojectDescriptor(descriptors.get(stepIndex));
					leafAttribute = setValue(descriptor, id, userValue, target,
							targetNodeType, isXlinkRef);
				}
			} else {
				// get parent of leaf attribute
				AttributeExpressionImpl ex = new AttributeExpressionImpl(steps
						.subList(0, stepIndex).toString(), new Hints(
						FeaturePropertyAccessorFactory.NAMESPACE_CONTEXT, ns));
				exValue = ex.evaluate(target);

				if (exValue != null) {
					if (exValue instanceof Collection) {
						// multi valued property
						Collection values = (Collection) exValue;

						if (values.isEmpty()) {
							// create the attribute
							leafAttribute = createAttributeTree(stepIndex - 1,
									parent, steps, descriptors, id, isXlinkRef,
									targetNodeType, userValue);

						} else {
							// grab existing node
						    Step currStep = steps.get(stepIndex);	
							if (currStep.isIndexed) {
								parent = (Attribute) Arrays.asList(
										(Collection) exValue).get(
										currStep.index);
							} else {
								// get the last node
								parent = (Attribute) Arrays.asList(
										(Collection) exValue).get(
										((Collection) exValue).size() - 1);
							}

							leafAttribute = createAttributeTree(stepIndex,
									parent, steps, descriptors, id, isXlinkRef,
									targetNodeType, userValue);
						}
					} else {
						parent = (Attribute) exValue;

						leafAttribute = createAttributeTree(stepIndex, parent,
								steps, descriptors, id, isXlinkRef,
								targetNodeType, userValue);
					}

				}
			}
			stepIndex--;
		}

		return leafAttribute;

	}
	
	private AttributeDescriptor reprojectDescriptor(
			AttributeDescriptor currDescriptor) {
		AttributeDescriptor descriptor;
		if (currDescriptor instanceof GeometryDescriptor && crs != null) {
			// important to maintain CRS information encoding
			GeometryType geomType = ((GeometryDescriptor) currDescriptor)
					.getType();
			GeometryType newGeomType = new GeometryTypeImpl(geomType.getName(),
					geomType.getBinding(), crs, geomType.isIdentified(),
					geomType.isAbstract(), geomType.getRestrictions(),
					geomType.getSuper(), geomType.getDescription());
			descriptor = descriptorFactory.createGeometryDescriptor(
					newGeomType, currDescriptor.getName(),
					currDescriptor.getMinOccurs(),
					currDescriptor.getMaxOccurs(), currDescriptor.isNillable(),
					null);
		} else {
			descriptor = currDescriptor;
		}
		return descriptor;
	}

	private Attribute createAttributeTree(int i, Attribute parent,
			StepList steps, List<AttributeDescriptor> descriptors, String id,
			boolean isXlinkRef, AttributeType targetNodeType, Object value) {
		// create from root to leaf attribute
		AttributeDescriptor descriptor;
		Attribute leafAttribute = null;
		while (i < steps.size()) {
			descriptor = reprojectDescriptor(descriptors.get(i));
			if (i < steps.size() - 1) {
				// not leaf attribute
				parent = setValue(descriptor, id, new ArrayList<Attribute>(),
						parent, targetNodeType, isXlinkRef);
			} else {
				// leaf attribute
				if (value != null) {
//					if (value instanceof Collection
//							&& !((Collection) value).isEmpty()) {
//						for (Object singleVal : (Collection) value) {
//							leafAttribute = setValue(descriptor, id, singleVal,
//									parent, targetNodeType, isXlinkRef);
//						}
//					} else {
						leafAttribute = setValue(descriptor, id, value, parent,
								targetNodeType, isXlinkRef);
//					}
				} else {
					leafAttribute = setValue(descriptor, id, null, parent,
							targetNodeType, isXlinkRef);
				}
			}
			i++;
		}
		return leafAttribute;
	}
    
    /**
     * Set a simple content value for an attribute.
     * 
     * @param attribute
     *            Attribute of simple content type.
     * @param value
     *            Value for the simple content.
     * @return The attribute with simple content type.
     */
    private Attribute setSimpleContentValue(Attribute attribute, Object value, AttributeDescriptor descriptor) {
        Property simpleContent = null;
        if (attribute instanceof ComplexAttribute) {
        	Collection values = ((ComplexAttribute)attribute).getValue();
        	if (!values.isEmpty() && values.size() == 1) {
          	    simpleContent = ((ComplexAttribute)attribute).getProperty(ComplexFeatureConstants.SIMPLE_CONTENT);
        	}
        }
        if (simpleContent == null) {
            Collection<Property> contents = new ArrayList<Property>();
            
            Object convertedValue = FF.literal(value).evaluate(value,
                    descriptor.getType().getBinding());
            simpleContent = new AttributeImpl(convertedValue, descriptor, null);
            contents.add(simpleContent);
            ArrayList<Attribute> nestedAttContents = new ArrayList<Attribute>();
            Attribute nestedAtt = new ComplexAttributeImpl(contents, attribute.getDescriptor(),
                    attribute.getIdentifier());
            nestedAttContents.add(nestedAtt);
            attribute.setValue(nestedAttContents);
            
            return nestedAtt;
        } else {
        	PropertyType simpleContentType = getSimpleContentType((AttributeType) simpleContent.getType());
            Object convertedValue = FF.literal(value).evaluate(value,
                    simpleContentType.getBinding());
        	simpleContent.setValue(convertedValue);
        	return attribute;
        }        
    }
    
    private Attribute setLeafAttribute(AttributeDescriptor currStepDescriptor,
            Step currStep, String id, Object value, Attribute parent,
            AttributeType targetNodeType, boolean isXlinkRef) {
//        int index = currStep.isIndexed ? currStep.getIndex() : -1;
        Attribute attribute = setValue(currStepDescriptor, id, value, parent,
                targetNodeType, isXlinkRef);
        return attribute;
    }

    @SuppressWarnings("unchecked")
    private Attribute setValue(final AttributeDescriptor descriptor, final String id,
            final Object value, final Attribute parent,
            final AttributeType targetNodeType, boolean isXlinkRef) {
        
        Object convertedValue = null;
        Map <Object, Object> simpleContentProperties = null;
        if (isFeatureChainedSimpleContent(descriptor, value)) {
            List<Property> nestedPropList = getSimpleContentList(value);
            if (!nestedPropList.isEmpty()) {
            	Property nestedProp = nestedPropList.iterator().next();
            	if (Types.isGeometryType(descriptor.getType())
            			|| nestedProp.getName().equals(descriptor.getName())) {
            		convertedValue = nestedProp.getValue();
            	} else {
                    convertedValue = nestedPropList;
            	}
                simpleContentProperties = nestedProp.getUserData();
            }
        } else {
            // adapt value to context
            convertedValue = convertValue(descriptor, value);   
        }
                
        Attribute leafAttribute = null;
        final Name attributeName = descriptor.getName();    
//            if (crs != null) {
//                builder.setCRS(crs);
//            }
//            builder.setDescriptor(parent.getDescriptor());
            // check for mapped type override
//            builder.setType(parent.getType());

            if (parent.getType().getName().equals(XSSchema.ANYTYPE_TYPE.getName())) {
                    // special handling for casting any type since there's no attributes in its
                    // schema
                    leafAttribute = builder.addAnyTypeValue(convertedValue, targetNodeType,
                            descriptor, id, crs);
            } else if (descriptor.getType().getName().equals(XSSchema.ANYTYPE_TYPE.getName())
                    && (value == null || (value instanceof Collection && ((Collection) value)
                            .isEmpty()))) {
                // casting anyType as a complex attribute so we can set xlink:href
                leafAttribute = builder.addComplexAnyTypeAttribute(convertedValue, descriptor, id);
            } else {
                leafAttribute = builder.add(id, convertedValue, attributeName, descriptor, crs);
            }
//            if (index > -1) {
//                // set attribute index if specified so it can be retrieved later for grouping
//                leafAttribute.getUserData().put(ComplexFeatureConstants.MAPPED_ATTRIBUTE_INDEX,
//                        index);
//            }
            List newValue = new ArrayList();
            newValue.addAll((Collection) parent.getValue());
            newValue.add(leafAttribute);
            parent.setValue(newValue);
//        }

        if (!isEmpty(convertedValue)) {
            leafAttribute.setValue(convertedValue);
        }
        if (simpleContentProperties != null) {
            mergeClientProperties(leafAttribute, simpleContentProperties);
        }
        return leafAttribute;
    }

    /**
     * Extract the simple content attribute from a list of features.
     * This is used when feature chaining is used for simple contents, such
     * as gml:name.. therefore the iterator would create a list of features containing the
     * simple content attributes. 
     * @param value    List of features
     * @return   The attribute with simple content
     */
    @SuppressWarnings("unchecked")
    private List<Property> getSimpleContentList(Object value) {
       if (value == null || !(value instanceof Collection)) {
           return null;
       }
       Collection list = (Collection) value;
       if (list.size() != 1) {
           // there should only 1 feature in a list even if it's multi-valued
           // since each value should be wrapped in its own parent node
           // eg. the format is
           // gsml:specification[1]/gsml:CompositionPart/...
           // gsml:specification[2]/gsml:CompositionPart/...
           throw new IllegalArgumentException("Expecting only 1 feature in the list!");
       }
       Object f = list.iterator().next();
       if (!(f instanceof Feature)) {
           throw new IllegalArgumentException("Expecting a feature!");
       }
       Feature feature = (Feature) f;
       ArrayList<Property> properties = new ArrayList<Property>();
       for (Property prop : feature.getProperties()) {
           if (!ComplexFeatureConstants.FEATURE_CHAINING_LINK_NAME.equals(prop.getName())) {
               properties.add(prop);
           }
       }
       return properties;
    }

    /**
     * Merge client properties from an attribute with a given map.
     * 
     * @param leafAttribute
     *            The attribute which will have the client properties
     * @param simpleContentProperties
     *            Map of new client properties
     */
    @SuppressWarnings("unchecked")
    private void mergeClientProperties(Attribute leafAttribute,
            Map<Object, Object> simpleContentProperties) {

        Map<Object, Object> origData = leafAttribute.getUserData();
        for (Object key : simpleContentProperties.keySet()) {
            if (key.equals(Attributes.class)) {
                // client properties
                Map inputMap = (Map) simpleContentProperties.get(key);
                if (origData.containsKey(Attributes.class)) {
                    // check each entry, and copy if it doesn't exist
                    Map existingMap = (Map) origData.get(key);
                    for (Object mapKey : inputMap.keySet()) {
                        if (!existingMap.containsKey(mapKey)) {
                            existingMap.put(mapKey, inputMap.get(mapKey));
                        }
                    }
                } else {
                    // copy the whole thing
                    origData.put(Attributes.class, inputMap);
                }
            } else {
                if (!origData.containsKey(key)) {
                    origData.put(key, simpleContentProperties.get(key));
                }
            }
        }
    }

    /**
     * Determine whether or not the value is a feature with target descriptor that is of the given
     * attribute descriptor. If it is, then it is a feature chained feature with only simple
     * content.
     * 
     * @param descriptor
     *            The attribute descriptor
     * @param value
     *            value to check
     * @return true if the value is an arraylist containing a feature with the descriptor.
     */
    @SuppressWarnings("unchecked")
    private boolean isFeatureChainedSimpleContent(AttributeDescriptor descriptor, Object value) {
        boolean isFeatureChainedSimpleContent = false;
        if (value != null) {
            if (value instanceof Collection) {
                Collection list = (Collection) value;
                if (!list.isEmpty() && list.size() == 1) {
                    Object f = list.iterator().next();
                    if (f instanceof Feature) {
                        Name featureName = ((Feature) f).getDescriptor().getName();
                        if (((Feature) f).getProperty(featureName) != null) {
                            isFeatureChainedSimpleContent = true;
                        }
                    }
                }
            }
        }
        return isFeatureChainedSimpleContent;
    }

    private boolean isEmpty(Object convertedValue) {
        if (convertedValue == null) {
            return true;
        } else if (convertedValue instanceof Collection && ((Collection) convertedValue).isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Return value converted into a type suitable for this descriptor.
     * 
     * @param descriptor
     * @param value
     * @return
     */
    @SuppressWarnings("serial")
    private Object convertValue(final AttributeDescriptor descriptor, final Object value) {
        final AttributeType type = descriptor.getType();
        Class<?> binding = type.getBinding();

        if (type instanceof ComplexType && binding == Collection.class) {
			if (!(value instanceof Collection)) {
				if (Types.isSimpleContentType(type)) {

					ArrayList<Property> list = new ArrayList<Property>();
					if (value == null && !descriptor.isNillable()) {
						return list;
					}
					list.add(buildSimpleContent(type, value));
					return list;
				} else {
					ArrayList list = new ArrayList();
					if (value != null) {
						list.add(value);
					}
					return list;
				}
			}
        }
        if (binding == String.class && value instanceof Collection) {
            // if it's a single value in a collection, strip the square brackets
            String collectionString = value.toString();
            return collectionString.substring(1, collectionString.length() - 1);
        }
        return FF.literal(value).evaluate(value, binding);
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

    /**
     * Create a fake property for simple content of a complex type.
     * 
     * @param type
     * @param value
     * @return
     */
    Attribute buildSimpleContent(AttributeType type, Object value) {
        AttributeType simpleContentType = getSimpleContentType(type);
        Object convertedValue = FF.literal(value).evaluate(value,
                getSimpleContentType(type).getBinding());
        AttributeDescriptor descriptor = new AttributeDescriptorImpl(simpleContentType,
                ComplexFeatureConstants.SIMPLE_CONTENT, 1, 1, true, (Object) null);
        return new AttributeImpl(convertedValue, descriptor, null);
    }

    public boolean isComplexType(final StepList attrXPath, final AttributeDescriptor featureType) {
        PropertyName attExp = FF.property(attrXPath.toString());
        Object type = attExp.evaluate(featureType);
        if (type == null) {
            type = attExp.evaluate(featureType);
            throw new IllegalArgumentException("path not found: " + attrXPath);
        }

        AttributeDescriptor node = (AttributeDescriptor) type;
        return node.getType() instanceof ComplexType;
    }

	public void setNamespace(NamespaceSupport namespaces) {
		this.ns = namespaces;
	}

}
