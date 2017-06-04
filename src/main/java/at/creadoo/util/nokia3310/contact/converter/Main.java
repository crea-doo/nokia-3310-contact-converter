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
package at.creadoo.util.nokia3310.contact.converter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.StringOptionHandler;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import at.creadoo.util.nokia3310.contact.converter.util.SimpleLocalizable;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Categories;
import ezvcard.property.FormattedName;
import ezvcard.property.Telephone;

public class Main {
	
	@Option(name = "-h", aliases = "--help", handler = BooleanOptionHandler.class, usage = "Show help information", required = false, help = true)
	private boolean help = false;

	@Option(name = "-q", aliases = "--quiet", handler = BooleanOptionHandler.class, usage = "Suppress any outputs", required = false)
	private boolean quiet = false;

	@Option(name = "-c", aliases = "--country-code", handler = StringOptionHandler.class, usage = "Country code used to parse phone numbers", required = false)
	private String countryCode = "AT";

	@Option(name = "-m", aliases = "--suffix-mobile", handler = StringOptionHandler.class, usage = "Suffix used for the contact when a mobile number is detected", required = false)
	private String suffixMobile = "";

	@Option(name = "-f", aliases = "--suffix-fixed-line", handler = StringOptionHandler.class, usage = "Suffix used for the contact when a fixed line number is detected", required = false)
	private String suffixFixedLine = "(Home)";

	@Option(name = "-u", aliases = "--suffix-uan", handler = StringOptionHandler.class, usage = "Suffix used for the contact when a Unversal Access Number is detected", required = false)
	private String suffixUAN = "(Work)";
	
	@Argument(index = 0, required = true, hidden = false, multiValued = false, metaVar = "inputFile", usage = "The file name of the input file")
	private File inputFile = null;
	
	@Argument(index = 1, required = false, hidden = false, multiValued = false, metaVar = "outputFile", usage = "The file name of the output file")
	private File outputFile = new File("backup.dat");

	public static void main(final String[] args) throws IOException {
		new Main().doMain(args);
	}

	public void doMain(final String[] args) throws IOException {
		final CmdLineParser parser = new CmdLineParser(this);

		// ----------
		parser.getProperties().withUsageWidth(80);
		final List<String> arguments = new ArrayList<String>();
		for (OptionHandler<?> s : parser.getArguments()) {
			arguments.add(s.option.metaVar());
		}
		// ----------
		
		try {
			parser.parseArgument(args);

			if (inputFile == null)
				throw new CmdLineException(parser, new SimpleLocalizable("No inputFile given"));

			if (!inputFile.exists())
				throw new CmdLineException(parser, new SimpleLocalizable("InputFile doesn't exist"));

			if (outputFile == null)
				throw new CmdLineException(parser, new SimpleLocalizable("No outputFile given"));

		} catch (CmdLineException ex) {
			if (!quiet) {
				System.err.println(ex.getMessage() + "\n");
				
				printUsage(parser, arguments);
			}
			return;
		}
		
		if (help) {
			printUsage(parser, arguments);
			return;
		}

		if (!quiet) {
			System.out.println("Convert file '" + inputFile + "' to '" + outputFile + "'");
			System.out.println("Read from '" + inputFile + "'");
		}
		
		final List<VCard> vCardsIn = new ArrayList<>();
		final List<VCard> vCardsOut = new ArrayList<>();
		
		try {
			vCardsIn.addAll(Ezvcard.parse(inputFile).all());
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
			return;
		}

		if (!quiet) {
			System.out.println("Number of contacts read: " + vCardsIn.size());
		}
		
		for (VCard vCard : vCardsIn) {
			final VCard vCardTemp = new VCard(vCard);
			
			if (!quiet) {
				System.out.println("Processing: '" + getSimpleName(vCardTemp) + "'");
			}
			
			// Clean up entries
			vCardTemp.setCategories((Categories) null);
			vCardTemp.setFormattedName((FormattedName) null);
			
			vCardTemp.removeExtendedProperty("X-ACCOUNT");
			vCardTemp.removeExtendedProperty("X-IRMC-LUID");
			
			
			if (vCardTemp.getTelephoneNumbers().size() > 1) {
				if (!quiet) {
					System.out.println("Multiple telephone numbers detected for '" + getSimpleName(vCardTemp) + "': Split contact to " + vCardTemp.getTelephoneNumbers().size() + " items");
				}
				
				for (Telephone telephone : vCardTemp.getTelephoneNumbers()) {
					VCard result = getVCardOnlyWithTelephone(vCardTemp, telephone);
					result = normalizeTelephoneNumber(result);
					result = addTelephoneType(result);
					
					vCardsOut.add(result);
				}
				
			} else {
				VCard result = normalizeTelephoneNumber(vCardTemp);
				
				vCardsOut.add(result);
			}
		}

		if (!quiet) {
			System.out.println("Write to '" + outputFile + "'");
			System.out.println("Number of contacts to write: " + vCardsOut.size());
		}
		
		try {
			Ezvcard.write(vCardsOut).prodId(false).versionStrict(true).version(VCardVersion.V2_1).go(outputFile);
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
			return;
		}
	}
	
	private static void printUsage(final CmdLineParser parser, final List<String> arguments) {
		System.err.println("Tool that takes contacts in the vcf format and converts to a file that can be imported and used with the new Nokia 3310 (2017).");
		System.err.println("\nUsage:");
		System.err.println("java -jar " + getJarFileName() + " [options...] " + StringUtils.join(arguments, " "));
		// print the list of available options
		parser.printUsage(System.err);
		System.err.println();
		
		// print option sample. This is useful some time
		System.err.println("  Example: java -jar " + getJarFileName() + parser.printExample(OptionHandlerFilter.REQUIRED) + " " + StringUtils.join(arguments, " "));
	}
	
	private static String getJarFileName() {
		try {
			return new java.io.File(Main.class.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.getPath())
					.getName();
		} catch (Throwable ex) {
			//
		}
		return "<empty>";
	}
	
	private VCard getVCardOnlyWithTelephone(final VCard vCard, final Telephone telephone) {
		final VCard result = new VCard(vCard);
		
		if (result.getTelephoneNumbers().size() > 0) {
			for (Iterator<Telephone> iterator = result.getTelephoneNumbers().iterator(); iterator.hasNext();) {
				Telephone item = iterator.next();
			    if (!item.equals(telephone)) {
			        iterator.remove();
			    }
			}
			
		}
		
		return result;
	}
	
	private VCard normalizeTelephoneNumber(final VCard vCard) {
		if (vCard.getTelephoneNumbers().size() > 0) {
			for (Iterator<Telephone> iterator = vCard.getTelephoneNumbers().iterator(); iterator.hasNext();) {
				final Telephone item = iterator.next();
				
				if (item.getText() != null) {
					item.getTypes().clear();
					//item.getTypes().remove(TelephoneType.WORK);
					item.getTypes().add(TelephoneType.CELL);
					item.getTypes().add(TelephoneType.VOICE);
					
					final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
					try {
						final PhoneNumber numberProto = phoneUtil.parse(item.getText(), countryCode);
						
						if (phoneUtil.isValidNumber(numberProto)) {
							item.setText(phoneUtil.format(numberProto, PhoneNumberFormat.E164));
						}
					} catch (NumberParseException e) {
						System.err.println("NumberParseException was thrown: " + e.toString());
					}
				}
			}
		}
		
		return vCard;
	}
	
	private String getSimpleName(final VCard vCard) {
		String result = "";
		if (vCard == null || vCard.getStructuredName() == null) {
			return result;
		}

		if (!StringUtils.isEmpty(vCard.getStructuredName().getGiven())) {
			result = vCard.getStructuredName().getGiven();
		}

		if (!StringUtils.isEmpty(vCard.getStructuredName().getFamily())) {
			result = result.trim() + " " + vCard.getStructuredName().getFamily();
		}
		
		return result.trim();
	}
	
	private VCard addTelephoneType(final VCard vCard) {
		if (vCard.getTelephoneNumbers().size() > 0) {
			final Telephone item = vCard.getTelephoneNumbers().get(0);
			
			if (item.getText() != null) {
				final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
				try {
					final PhoneNumber numberProto = phoneUtil.parse(item.getText(), countryCode);
					
					if (phoneUtil.isValidNumber(numberProto)) {
						final PhoneNumberType numberType = phoneUtil.getNumberType(numberProto);
						
						switch (numberType) {
						case MOBILE:
							item.getTypes().add(TelephoneType.CELL);
							if (!StringUtils.isEmpty(suffixMobile)) {
								vCard.getStructuredName().setGiven(vCard.getStructuredName().getGiven() + " " + suffixMobile);
								vCard.getStructuredName().getSuffixes().add(suffixMobile);
							}
							break;
						case UAN:
							item.getTypes().add(TelephoneType.WORK);
							if (!StringUtils.isEmpty(suffixUAN)) {
								vCard.getStructuredName().setGiven(vCard.getStructuredName().getGiven() + " " + suffixUAN);
								vCard.getStructuredName().getSuffixes().add(suffixUAN);
							}
							break;
						default:
							item.getTypes().add(TelephoneType.HOME);
							if (!StringUtils.isEmpty(suffixFixedLine)) {
								vCard.getStructuredName().setGiven(vCard.getStructuredName().getGiven() + " " + suffixFixedLine);
								vCard.getStructuredName().getSuffixes().add(suffixFixedLine);
							}
							break;
						}
					}
				} catch (NumberParseException e) {
					System.err.println("NumberParseException was thrown: " + e.toString());
				}
			}
		}
		return vCard;
	}

}
