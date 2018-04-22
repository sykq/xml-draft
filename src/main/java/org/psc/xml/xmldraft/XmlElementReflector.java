package org.psc.xml.xmldraft;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmlElementReflector {
	private static final Logger LOGGER = LoggerFactory.getLogger(XmlElementReflector.class);

	private static final String DEFAULT_NAMESPACE_PLACEHOLDER = "##default";

	private static final Map<CompositeType, Map<String, String>> TYPE_MAPPING = new ConcurrentHashMap<>();

	public static Map<String, String> getNamespaceMapping(Class<?> type) {
		CompositeType compType = initCompositeType(type);

		if (!TYPE_MAPPING.containsKey(compType)) {
			resolveType(compType);

			for (Class<?> referencedType : compType.getReferencedTypes()) {
				CompositeType referencedCompType = initCompositeType(referencedType);

				if (!TYPE_MAPPING.containsKey(referencedCompType)) {
					getNamespaceMapping(referencedType);
				}
			}
		}

		return getNamespaceMapping(compType);
	}

	private static CompositeType initCompositeType(Class<?> type) {
		CompositeType compType = new CompositeType();
		compType.setType(type);

		return compType;
	}

	private static Map<String, String> getNamespaceMapping(CompositeType compType) {
		// TODO: this might fail since it's possible that multiple elements with the
		// same name (key) and different namespaces could exist...
		Map<String, String> compositeNamespaces = new HashMap<>();
		compositeNamespaces.putAll(TYPE_MAPPING.get(compType));
		compType.getReferencedTypes().stream().map(XmlElementReflector::initCompositeType)
				.forEach(e -> compositeNamespaces.putAll(TYPE_MAPPING.get(e)));
		return compositeNamespaces;
	}

	private static void resolveType(CompositeType compType) {
		Field[] fields;

		Map<String, String> namespaceMapping = new HashMap<>();
		Class<?> clazz = compType.getType();
		XmlType enclosingXmlType = clazz.getAnnotation(XmlType.class);
		XmlRootElement enclosingXmlRootElement = clazz.getAnnotation(XmlRootElement.class);

		if (enclosingXmlType == null && enclosingXmlRootElement == null) {
			throw new RuntimeException("no @XmlType or @XmlRootElement annotation found for class " + clazz.getName());
		}

		String enclosingType;
		String enclosingNamespace;

		if (enclosingXmlType != null && StringUtils.isNotBlank(enclosingXmlType.name())) {
			enclosingType = enclosingXmlType.name();
			enclosingNamespace = enclosingXmlType.namespace();
		} else if (enclosingXmlRootElement != null) {
			enclosingType = enclosingXmlRootElement.name();
			enclosingNamespace = enclosingXmlRootElement.namespace();
		} else {
			LOGGER.error("no valid name and/or namespace found for class {}", clazz.getName());
			throw new RuntimeException("no valid name and/or namespace found for class " + clazz.getName());
		}

		namespaceMapping.put(enclosingType, enclosingNamespace);
		fields = clazz.getDeclaredFields();

		for (Field field : fields) {
			field.setAccessible(true);
			XmlElement element = field.getDeclaredAnnotation(XmlElement.class);

			if (element != null) {
				String namespace = !StringUtils.equals(element.namespace(), DEFAULT_NAMESPACE_PLACEHOLDER)
						? element.namespace()
						: enclosingNamespace;
				namespaceMapping.put(element.name(), namespace);
				addReference(compType, field);
			} else {
				XmlAttribute attribute = field.getDeclaredAnnotation(XmlAttribute.class);

				if (attribute != null) {
					String namespace = !StringUtils.equals(attribute.namespace(), DEFAULT_NAMESPACE_PLACEHOLDER)
							? attribute.namespace()
							: enclosingNamespace;
					namespaceMapping.put(attribute.name(), namespace);
					// attributes can only be simple types, therefore no need to check/add it as a
					// referenced type
				}
			}

			field.setAccessible(false);
		}

		TYPE_MAPPING.put(compType, namespaceMapping);
	}

	private static void addReference(CompositeType compType, Field field) {
		Class<?> fieldType = getType(field);
		LOGGER.info(compType.getType().getSimpleName() + " check addReference: " + fieldType.getSimpleName());
		if (fieldType.getPackage().equals(compType.getType().getPackage()) && !fieldType.isEnum()
				&& hasValidNameInAnnotation(fieldType)) {
			compType.getReferencedTypes().add(fieldType);
			LOGGER.info(compType.getType().getSimpleName() + " reference: " + fieldType.getSimpleName() + " added.");
		}
		/*
		 * if (!fieldType.equals(String.class) && !fieldType.isPrimitive() &&
		 * !fieldType.isEnum() && !fieldType.equals(GregorianCalendar.class) &&
		 * !fieldType.equals(XMLGregorianCalendar.class)) {
		 * compType.getReferencedTypes().add(fieldType); }
		 */
	}

	private static boolean hasValidNameInAnnotation(Class<?> clazz) {
		boolean hasValidName = false;

		XmlType enclosingXmlType = clazz.getAnnotation(XmlType.class);
		XmlRootElement enclosingXmlRootElement = clazz.getAnnotation(XmlRootElement.class);

		if ((enclosingXmlType != null && StringUtils.isNotBlank(enclosingXmlType.name()))
				|| (enclosingXmlRootElement != null && StringUtils.isNotBlank(enclosingXmlRootElement.name()))) {
			hasValidName = true;
		} else {
			LOGGER.info("no valid name and/or namespace found for class {}", clazz.getSimpleName());
		}

		return hasValidName;
	}

	private static Class<?> getType(Field field) {
		Class<?> type = field.getType();
		Type genericType = field.getGenericType();

		if (genericType instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) genericType;
			type = (Class<?>) parameterizedType.getActualTypeArguments()[0];
		}

		return type;
	}

	private static class CompositeType {
		private Class<?> type;
		private List<Class<?>> referencedTypes = new ArrayList<>();

		/**
		 * @return the type
		 */
		public Class<?> getType() {
			return type;
		}

		/**
		 * @param type
		 *            the type to set
		 */
		public void setType(Class<?> type) {
			this.type = type;
		}

		/**
		 * @return the referencedTypes
		 */
		public List<Class<?>> getReferencedTypes() {
			return referencedTypes;
		}

		/**
		 * @param referencedTypes
		 *            the referencedTypes to set
		 */
		@SuppressWarnings("unused")
		public void setReferencedTypes(List<Class<?>> referencedTypes) {
			this.referencedTypes = referencedTypes;
		}

		@Override
		public boolean equals(Object other) {
			return type.equals(((CompositeType) other).getType());
		}

		@Override
		public int hashCode() {
			return type.hashCode();
		}

	}
}
