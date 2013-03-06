/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2011, Open Source Geospatial Foundation (OSGeo)
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
import java.util.List;
import java.util.NoSuchElementException;

import org.geotools.data.joining.JoiningNestedAttributeMapping;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.NestedAttributeImpl;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.filter.Filter;

/**
 * An extension to {@linkplain org.geotools.data.complex.DataAccessMappingFeatureIterator} where
 * filter is present. Unlike with FilteringMappingFeatureIterator The filter is applied on
 * the complex feature
 * 
 * @author Niels Charlier (Curtin University of Technology)
 *
 * @source $URL$
 */
public class PostFilteringMappingFeatureIterator implements IMappingFeatureIterator {

//	private List<DataAccessMappingFeatureIterator> nestedFeatures;
    protected DataAccessMappingFeatureIterator delegate;
    protected DataAccessMappingFeatureIterator filteringIterator;
    protected Feature next;
    protected Filter filter;
    protected int maxFeatures;
    protected int count = 0;
    
    public PostFilteringMappingFeatureIterator(DataAccessMappingFeatureIterator iterator, Filter filter, int maxFeatures) throws IOException {
        this.delegate = iterator;
        this.filter = filter;
        this.maxFeatures = maxFeatures;
        this.filteringIterator = new DataAccessMappingFeatureIterator(iterator);

			// make a copy of nested feature iterators since evaluating filters
			// requires building the nested features
			// and since the order is important for joining, we shouldn't
			// disrupt the original nested feature iterator
			// for encoding
//			this.nestedFeatures = new ArrayList<DataAccessMappingFeatureIterator>();
//			for (AttributeMapping attMapping : ((AbstractMappingFeatureIterator) delegate).selectedMapping) {
//				if (attMapping instanceof JoiningNestedAttributeMapping) {
//					((JoiningNestedAttributeMapping) attMapping).open(this, ((AbstractMappingFeatureIterator)delegate).query);					
//				}
//			}
	

//        next = getFilteredNext();
    }

    public void close() {

//        if (delegate instanceof AbstractMappingFeatureIterator) {
//			for (AttributeMapping attMapping : ((AbstractMappingFeatureIterator) delegate).selectedMapping) {
//				if (attMapping instanceof JoiningNestedAttributeMapping) {
//					((JoiningNestedAttributeMapping) attMapping).close(this);
//					// close the children too
//				}
//			}
//		}
        delegate.close(); 
        delegate = null;
        
        filteringIterator.close();
        filteringIterator = null;
        
        next = null;
        filter = null;
//		if (nestedFeatures != null) {
//			for (DataAccessMappingFeatureIterator iterator : nestedFeatures) {
//				iterator.close();
//			}
//			this.nestedFeatures = null;
//		}
    } 
  
    protected Feature getFilteredNext() {
        while (filteringIterator.hasNext() && count < maxFeatures) {            
            delegate.hasNext();
            Feature filteredNext = filteringIterator.next();
            try {
                if (filter.evaluate(filteredNext)) {
//            	if (filter.evaluate(next)) {
                    return delegate.next();
                }
            } catch (NullPointerException e) {
                // ignore this exception
                // this is to cater the case if the attribute has no value and 
                // has been skipped in the delegate DataAccessMappingFeatureIterator
            }
            try {
            	// if doesn't match, skip
				delegate.skip();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
        }
//        for (Property prop : next.getProperties()) {
//        	// reset the nested features cursor position
//        	if (prop instanceof NestedAttributeImpl) {
//        		try {
//					((NestedAttributeImpl)prop).resetIterator();
//				} catch (IOException e) {
//					// throw exception.. failed to reset iterator
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//        	}
//        }
//        if (delegate instanceof AbstractMappingFeatureIterator) {
//        	// reset the nested features cursor position
//        	for (AttributeMapping attMapping : ((AbstractMappingFeatureIterator) delegate).selectedMapping) {
//        		if (attMapping instanceof JoiningNestedAttributeMapping) {
//        			((JoiningNestedAttributeMapping) attMapping).reset(delegate);
//        		}
//    		}       
//        }
        return null;
    }

    public boolean hasNext() {    
    	next = getFilteredNext();
        return next != null;
    }
        
    public Feature next() {
        if(next == null){
            throw new NoSuchElementException();
        }
        
        count++;
//        Feature current = next;
//        next = getFilteredNext();
//        return current;
        return next;
    }

    public void remove() {
        throw new UnsupportedOperationException();
        
    }

	@Override
	public boolean isAvailable() {
		// TODO Auto-generated method stub
		return next != null;
	}

}
