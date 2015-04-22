package ca.uhn.fhir.context;

/*
 * #%L
 * HAPI FHIR - Core Library
 * %%
 * Copyright (C) 2014 - 2015 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.text.WordUtils;
import org.hl7.fhir.instance.model.IBase;
import org.hl7.fhir.instance.model.IBaseResource;
import org.hl7.fhir.instance.model.api.IReference;

import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.base.composite.BaseResourceReferenceDt;

public class RuntimeChildUndeclaredExtensionDefinition extends BaseRuntimeChildDefinition {

	private static final String VALUE_REFERENCE = "valueReference";
	private static final String VALUE_RESOURCE = "valueResource";
	private Map<String, BaseRuntimeElementDefinition<?>> myAttributeNameToDefinition;
	private Map<Class<? extends IBase>, String> myDatatypeToAttributeName;
	private Map<Class<? extends IBase>, BaseRuntimeElementDefinition<?>> myDatatypeToDefinition;

	public RuntimeChildUndeclaredExtensionDefinition() {
		// nothing
	}

	@Override
	public IAccessor getAccessor() {
		return new IAccessor() {
			@Override
			public List<IBase> getValues(Object theTarget) {
				ExtensionDt target = (ExtensionDt) theTarget;
				if (target.getValue() != null) {
					return Collections.singletonList((IBase) target.getValue());
				}
				ArrayList<IBase> retVal = new ArrayList<IBase>(target.getUndeclaredExtensions());
				return retVal;
			}
		};
	}

	@Override
	public BaseRuntimeElementDefinition<?> getChildByName(String theName) {
		return myAttributeNameToDefinition.get(theName);
	}

	@Override
	public BaseRuntimeElementDefinition<?> getChildElementDefinitionByDatatype(Class<? extends IBase> theType) {
		Class<? extends IBase> type = theType;
//		if (IReference.type)
		return myDatatypeToDefinition.get(type);
	}

	@Override
	public String getChildNameByDatatype(Class<? extends IBase> theDatatype) {
		return myDatatypeToAttributeName.get(theDatatype);
	}

	@Override
	public String getElementName() {
		return "extension";
	}

	@Override
	public int getMax() {
		return 1;
	}

	@Override
	public int getMin() {
		return 0;
	}

	@Override
	public IMutator getMutator() {
		return new IMutator() {
			@Override
			public void addValue(Object theTarget, IBase theValue) {
				ExtensionDt target = (ExtensionDt) theTarget;
				if (theValue instanceof IDatatype) {
					target.setValue((IDatatype) theTarget);
				} else {
					target.getUndeclaredExtensions().add((ExtensionDt) theValue);
				}
			}
		};
	}

	@Override
	public Set<String> getValidChildNames() {
		return myAttributeNameToDefinition.keySet();
	}

	@Override
	void sealAndInitialize(FhirContext theContext, Map<Class<? extends IBase>, BaseRuntimeElementDefinition<?>> theClassToElementDefinitions) {
		Map<String, BaseRuntimeElementDefinition<?>> datatypeAttributeNameToDefinition = new HashMap<String, BaseRuntimeElementDefinition<?>>();
		myDatatypeToAttributeName = new HashMap<Class<? extends IBase>, String>();
		myDatatypeToDefinition = new HashMap<Class<? extends IBase>, BaseRuntimeElementDefinition<?>>();

		for (BaseRuntimeElementDefinition<?> next : theClassToElementDefinitions.values()) {
			if (next instanceof IRuntimeDatatypeDefinition) {
				// if (next.getName().equals("CodeableConcept")) {
				// System.out.println();
				// }

				myDatatypeToDefinition.put(next.getImplementingClass(), next);

				if (!((IRuntimeDatatypeDefinition) next).isSpecialization()) {
					String attrName = createExtensionChildName(next);
					if (datatypeAttributeNameToDefinition.containsKey(attrName)) {
						throw new ConfigurationException("More than one child matches attribute name " + attrName);
					}
					datatypeAttributeNameToDefinition.put(attrName, next);
					datatypeAttributeNameToDefinition.put(attrName.toLowerCase(), next);
					myDatatypeToAttributeName.put(next.getImplementingClass(), attrName);
				}
			}
		}

		myAttributeNameToDefinition = datatypeAttributeNameToDefinition;


//		for (Entry<String, BaseRuntimeElementDefinition<?>> next : myAttributeNameToDefinition.entrySet()) {
//			@SuppressWarnings("unchecked")
//			Class<? extends IDatatype> type = (Class<? extends IDatatype>) next.getValue().getImplementingClass();
//			myDatatypeToDefinition.put(type, next.getValue());
//		}

		/*
		 * Resource reference - The correct name is 'valueReference' in DSTU2 and 'valueResource' in DSTU1
		 */
		addReferenceBinding(theContext, theClassToElementDefinitions, VALUE_RESOURCE);
		addReferenceBinding(theContext, theClassToElementDefinitions, VALUE_REFERENCE);
	}

	private void addReferenceBinding(FhirContext theContext, Map<Class<? extends IBase>, BaseRuntimeElementDefinition<?>> theClassToElementDefinitions, String value) {
		List<Class<? extends IBaseResource>> types = new ArrayList<Class<? extends IBaseResource>>();
		types.add(IBaseResource.class);
		RuntimeResourceReferenceDefinition def = new RuntimeResourceReferenceDefinition(value, types);
		def.sealAndInitialize(theContext, theClassToElementDefinitions);

		myAttributeNameToDefinition.put(value, def);
		/*
		 * Resource reference - The correct name is 'valueReference' in DSTU2 and 'valueResource' in DSTU1
		 */
		boolean dstu1 = (theContext.getVersion().getVersion().equals(FhirVersionEnum.DSTU1));
		if ((dstu1 && (value != VALUE_REFERENCE)) || (!dstu1 && (value != VALUE_RESOURCE))) {
			myDatatypeToAttributeName.put(theContext.getVersion().getResourceReferenceType(), value);
			myDatatypeToDefinition.put(BaseResourceReferenceDt.class, def);
			myDatatypeToDefinition.put(theContext.getVersion().getResourceReferenceType(), def);
		}

	}

	public static String createExtensionChildName(BaseRuntimeElementDefinition<?> next) {
		String attrName = "value" + WordUtils.capitalize(next.getName());
		return attrName;
	}

}
