/*
 * Copyright 2017 crea-doo.at
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package at.creadoo.util.nokia3310.contact.converter.util;

import org.kohsuke.args4j.Localizable;

import java.util.Locale;

public class SimpleLocalizable implements Localizable {

	private final String message;

	public SimpleLocalizable(final String message) {
		this.message = message;
	}

	@Override
	public String format(final Object... args) {
		return message;
	}

	@Override
	public String formatWithLocale(final Locale locale, final Object... args) {
		return message;
	}
	
}