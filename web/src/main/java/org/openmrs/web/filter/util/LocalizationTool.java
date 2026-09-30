/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.web.filter.util;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.util.LocaleUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is intended for accessing {@link ResourceBundle} and formatting messages therein. It
 * is exposed to the setup wizard's Velocity templates as <code>$l10n</code>, where
 * <code>$l10n.get("code")</code> renders a message and <code>$l10n.get("code").insert(arg)</code>
 * renders it with arguments.
 */
public class LocalizationTool {

	/**
	 * The name under which this tool is exposed to templates
	 */
	public static final String KEY = "l10n";

	private static final Logger log = LoggerFactory.getLogger(LocalizationTool.class);

	private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");

	/**
	 * The default message resource bundle to use, this is english
	 */
	private static ResourceBundle defaultResourceBundle = null;

	private Locale locale;

	public LocalizationTool(Locale locale) {
		this.locale = locale;
	}

	public Locale getLocale() {
		return locale;
	}

	public void setLocale(Locale locale) {
		this.locale = locale;
	}

	/**
	 * @return the defaultResourceBundle
	 */
	public static ResourceBundle getDefaultResourceBundle() {
		if (defaultResourceBundle == null) {
			defaultResourceBundle = CustomResourceLoader.getInstance(null).getResourceBundle(Locale.ENGLISH);
		}
		return defaultResourceBundle;
	}

	/**
	 * Returns the message with the given code in the current locale. The message text is looked up when
	 * it is rendered.
	 *
	 * @param code the message code
	 * @return the message, which renders as an empty string if code is null
	 */
	public Message get(Object code) {
		return new Message(code == null ? null : String.valueOf(code), locale, null);
	}

	/**
	 * Loads resource bundles from outside the class path, defaulting to messages.properties if there is
	 * no messages_XX.properties file for the locale
	 */
	protected ResourceBundle getBundle(Object loc) {
		Locale bundleLocale = (loc == null) ? getLocale() : LocaleUtility.fromSpecification(String.valueOf(loc));
		if (bundleLocale == null) {
			return null;
		}
		ResourceBundle rb = CustomResourceLoader.getInstance(null).getResourceBundle(bundleLocale);
		if (rb == null) {
			rb = getDefaultResourceBundle();
		}
		return rb;
	}

	/**
	 * Returns the raw message text, using the english equivalent if the translation is blank
	 */
	Object getRaw(String code, Object loc) {
		Object msg = find(code, loc);
		if (msg == null || StringUtils.isBlank(msg.toString())) {
			msg = find(code, Locale.ENGLISH.toString());
		}
		return msg;
	}

	private Object find(String code, Object loc) {
		ResourceBundle rb = getBundle(loc);
		if (rb == null) {
			return null;
		}
		try {
			return rb.getObject(code);
		} catch (MissingResourceException e) {
			return null;
		}
	}

	/**
	 * Doubles each apostrophe that isn't already doubled, so that {@link MessageFormat} renders it
	 * literally. Translations write apostrophes both ways: most as plain text, and some doubled as
	 * Spring's {@link org.springframework.context.MessageSource} expects for messages with arguments.
	 *
	 * @param pattern the message text
	 * @return the text with every apostrophe escaped for {@link MessageFormat}
	 */
	static String escapeApostrophes(String pattern) {
		return LONE_APOSTROPHE.matcher(pattern).replaceAll("''");
	}

	/**
	 * A message which renders to its localized text. Like Spring's
	 * {@link org.springframework.context.MessageSource}, a message without arguments renders as-is, and
	 * one with inserted arguments is formatted with {@link MessageFormat}, with apostrophes kept as
	 * text. A missing message renders as <code>???code???</code>.
	 */
	public final class Message {

		private final String code;

		private final Locale messageLocale;

		private final Object[] args;

		private Message(String code, Locale messageLocale, Object[] args) {
			this.code = code;
			this.messageLocale = messageLocale;
			this.args = args;
		}

		/**
		 * @param newArgs arguments to append to those already inserted
		 * @return a message with the combined arguments
		 */
		public Message insert(Object[] newArgs) {
			if (newArgs == null) {
				return this;
			}
			Object[] combined;
			if (args == null) {
				combined = newArgs.clone();
			} else {
				combined = new Object[args.length + newArgs.length];
				System.arraycopy(args, 0, combined, 0, args.length);
				System.arraycopy(newArgs, 0, combined, args.length, newArgs.length);
			}
			return new Message(code, messageLocale, combined);
		}

		public Message insert(List<?> newArgs) {
			return insert(newArgs.toArray());
		}

		public Message insert(Object arg) {
			return insert(new Object[] { arg });
		}

		public Message insert(Object arg1, Object arg2) {
			return insert(new Object[] { arg1, arg2 });
		}

		@Override
		public String toString() {
			if (code == null) {
				return "";
			}
			Object raw = getRaw(code, messageLocale);
			if (raw == null) {
				log.warn("missing key: {}", code);
				return "???" + code + "???";
			}
			String text = String.valueOf(raw);
			if (args == null || args.length == 0) {
				return text;
			}
			return MessageFormat.format(escapeApostrophes(text), args);
		}
	}
}
