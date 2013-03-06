package org.geotools.data.joining;

import java.io.IOException;

import org.geotools.data.complex.DataAccessMappingFeatureIterator;
import org.geotools.data.complex.IMappingFeatureIterator;
import org.opengis.feature.Feature;
import org.opengis.filter.expression.Expression;

public class XlinkSourceIterator implements IMappingFeatureIterator {
	private DataAccessMappingFeatureIterator featureIterator;
	private Expression nestedSourceExpression;
	private Object foreignKeyValue;

	public XlinkSourceIterator(DataAccessMappingFeatureIterator iterator, Expression nestedSourceExpression, Object fKeyValue) {
		this.featureIterator = iterator;
		this.nestedSourceExpression = nestedSourceExpression;
		this.foreignKeyValue = fKeyValue;
	}

	@Override
	public boolean hasNext() {
		// close would be performed in the underlying feature iterator hasNext()
		if (featureIterator.hasNext()
              && featureIterator.peekNextValue(nestedSourceExpression).toString()
                      .equals(foreignKeyValue.toString())) {
          return true;
        }
		return false;
	}

	@Override
	public Feature next() {
		try {
			return featureIterator.getNextXlinkSource();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void remove() {
		// TODO Auto-generated method stub
		// throw exception
	}
	
	@Override
	public void close() {
		featureIterator.close();
	}
	
	@Override
	public boolean isAvailable() {
		return featureIterator.isAvailable();
	}

	public DataAccessMappingFeatureIterator getComplexFeatureIterator() {
		return featureIterator;
	}
}
