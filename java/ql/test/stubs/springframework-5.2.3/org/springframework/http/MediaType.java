/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.http;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;

/**
 * A subclass of {@link MimeType} that adds support for quality parameters
 * as defined in the HTTP specification.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 * @author Kazuki Shimizu
 * @author Sam Brannen
 * @since 3.0
 * @see <a href="https://tools.ietf.org/html/rfc7231#section-3.1.1.1">
 *     HTTP 1.1: Semantics and Content, section 3.1.1.1</a>
 */
public class MediaType extends MimeType implements Serializable {

	/**
	 * Public constant media type that includes all media ranges (i.e. "&#42;/&#42;").
	 */
	public static final MediaType ALL;

	/**
	 * A String equivalent of {@link MediaType#ALL}.
	 */
	public static final String ALL_VALUE = "";

	/**
	 *  Public constant media type for {@code application/atom+xml}.
	 */
	public static final MediaType APPLICATION_ATOM_XML;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_ATOM_XML}.
	 */
	public static final String APPLICATION_ATOM_XML_VALUE = "";

	/**
	 * Public constant media type for {@code application/cbor}.
	 * @since 5.2
	 */
	public static final MediaType APPLICATION_CBOR;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_CBOR}.
	 * @since 5.2
	 */
	public static final String APPLICATION_CBOR_VALUE = "";

	/**
	 * Public constant media type for {@code application/x-www-form-urlencoded}.
	 */
	public static final MediaType APPLICATION_FORM_URLENCODED;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_FORM_URLENCODED}.
	 */
	public static final String APPLICATION_FORM_URLENCODED_VALUE = "";

	/**
	 * Public constant media type for {@code application/json}.
	 */
	public static final MediaType APPLICATION_JSON;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_JSON}.
	 * @see #APPLICATION_JSON_UTF8_VALUE
	 */
	public static final String APPLICATION_JSON_VALUE = "";

	/**
	 * Public constant media type for {@code application/json;charset=UTF-8}.
	 * @deprecated as of 5.2 in favor of {@link #APPLICATION_JSON}
	 * since major browsers like Chrome
	 * <a href="https://bugs.chromium.org/p/chromium/issues/detail?id=438464">
	 * now comply with the specification</a> and interpret correctly UTF-8 special
	 * characters without requiring a {@code charset=UTF-8} parameter.
	 */
	@Deprecated
	public static final MediaType APPLICATION_JSON_UTF8;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_JSON_UTF8}.
	 * @deprecated as of 5.2 in favor of {@link #APPLICATION_JSON_VALUE}
	 * since major browsers like Chrome
	 * <a href="https://bugs.chromium.org/p/chromium/issues/detail?id=438464">
	 * now comply with the specification</a> and interpret correctly UTF-8 special
	 * characters without requiring a {@code charset=UTF-8} parameter.
	 */
	@Deprecated
	public static final String APPLICATION_JSON_UTF8_VALUE = "";

	/**
	 * Public constant media type for {@code application/octet-stream}.
	 */
	public static final MediaType APPLICATION_OCTET_STREAM;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_OCTET_STREAM}.
	 */
	public static final String APPLICATION_OCTET_STREAM_VALUE = "";

	/**
	 * Public constant media type for {@code application/pdf}.
	 * @since 4.3
	 */
	public static final MediaType APPLICATION_PDF;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_PDF}.
	 * @since 4.3
	 */
	public static final String APPLICATION_PDF_VALUE = "";

	/**
	 * Public constant media type for {@code application/problem+json}.
	 * @since 5.0
	 * @see <a href="https://tools.ietf.org/html/rfc7807#section-6.1">
	 *     Problem Details for HTTP APIs, 6.1. application/problem+json</a>
	 */
	public static final MediaType APPLICATION_PROBLEM_JSON;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_PROBLEM_JSON}.
	 * @since 5.0
	 */
	public static final String APPLICATION_PROBLEM_JSON_VALUE = "";

	/**
	 * Public constant media type for {@code application/problem+json}.
	 * @since 5.0
	 * @see <a href="https://tools.ietf.org/html/rfc7807#section-6.1">
	 *     Problem Details for HTTP APIs, 6.1. application/problem+json</a>
	 * @deprecated as of 5.2 in favor of {@link #APPLICATION_PROBLEM_JSON}
	 * since major browsers like Chrome
	 * <a href="https://bugs.chromium.org/p/chromium/issues/detail?id=438464">
	 * now comply with the specification</a> and interpret correctly UTF-8 special
	 * characters without requiring a {@code charset=UTF-8} parameter.
	 */
	@Deprecated
	public static final MediaType APPLICATION_PROBLEM_JSON_UTF8;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_PROBLEM_JSON_UTF8}.
	 * @since 5.0
	 * @deprecated as of 5.2 in favor of {@link #APPLICATION_PROBLEM_JSON_VALUE}
	 * since major browsers like Chrome
	 * <a href="https://bugs.chromium.org/p/chromium/issues/detail?id=438464">
	 * now comply with the specification</a> and interpret correctly UTF-8 special
	 * characters without requiring a {@code charset=UTF-8} parameter.
	 */
	@Deprecated
	public static final String APPLICATION_PROBLEM_JSON_UTF8_VALUE = "";

	/**
	 * Public constant media type for {@code application/problem+xml}.
	 * @since 5.0
	 * @see <a href="https://tools.ietf.org/html/rfc7807#section-6.2">
	 *     Problem Details for HTTP APIs, 6.2. application/problem+xml</a>
	 */
	public static final MediaType APPLICATION_PROBLEM_XML;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_PROBLEM_XML}.
	 * @since 5.0
	 */
	public static final String APPLICATION_PROBLEM_XML_VALUE = "";

	/**
	 * Public constant media type for {@code application/rss+xml}.
	 * @since 4.3.6
	 */
	public static final MediaType APPLICATION_RSS_XML;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_RSS_XML}.
	 * @since 4.3.6
	 */
	public static final String APPLICATION_RSS_XML_VALUE = "";

	/**
	 * Public constant media type for {@code application/x-ndjson}.
	 * @since 5.3
	 */
	public static final MediaType APPLICATION_NDJSON;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_NDJSON}.
	 * @since 5.3
	 */
	public static final String APPLICATION_NDJSON_VALUE = "";

	/**
	 * Public constant media type for {@code application/stream+json}.
	 * @deprecated as of 5.3, see notice on {@link #APPLICATION_STREAM_JSON_VALUE}.
	 * @since 5.0
	 */
	@Deprecated
	public static final MediaType APPLICATION_STREAM_JSON;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_STREAM_JSON}.
	 * @deprecated as of 5.3 since it originates from the W3C Activity Streams
	 * specification which has a more specific purpose and has been since
	 * replaced with a different mime type. Use {@link #APPLICATION_NDJSON} as
	 * a replacement or any other line-delimited JSON format (e.g. JSON Lines,
	 * JSON Text Sequences).
	 * @since 5.0
	 */
	@Deprecated
	public static final String APPLICATION_STREAM_JSON_VALUE = "";

	/**
	 * Public constant media type for {@code application/xhtml+xml}.
	 */
	public static final MediaType APPLICATION_XHTML_XML;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_XHTML_XML}.
	 */
	public static final String APPLICATION_XHTML_XML_VALUE = "";

	/**
	 * Public constant media type for {@code application/xml}.
	 */
	public static final MediaType APPLICATION_XML;

	/**
	 * A String equivalent of {@link MediaType#APPLICATION_XML}.
	 */
	public static final String APPLICATION_XML_VALUE = "";

	/**
	 * Public constant media type for {@code image/gif}.
	 */
	public static final MediaType IMAGE_GIF;

	/**
	 * A String equivalent of {@link MediaType#IMAGE_GIF}.
	 */
	public static final String IMAGE_GIF_VALUE = "";

	/**
	 * Public constant media type for {@code image/jpeg}.
	 */
	public static final MediaType IMAGE_JPEG;

	/**
	 * A String equivalent of {@link MediaType#IMAGE_JPEG}.
	 */
	public static final String IMAGE_JPEG_VALUE = "";

	/**
	 * Public constant media type for {@code image/png}.
	 */
	public static final MediaType IMAGE_PNG;

	/**
	 * A String equivalent of {@link MediaType#IMAGE_PNG}.
	 */
	public static final String IMAGE_PNG_VALUE = "";

	/**
	 * Public constant media type for {@code multipart/form-data}.
	 */
	public static final MediaType MULTIPART_FORM_DATA;

	/**
	 * A String equivalent of {@link MediaType#MULTIPART_FORM_DATA}.
	 */
	public static final String MULTIPART_FORM_DATA_VALUE = "";

	/**
	 * Public constant media type for {@code multipart/mixed}.
	 * @since 5.2
	 */
	public static final MediaType MULTIPART_MIXED;

	/**
	 * A String equivalent of {@link MediaType#MULTIPART_MIXED}.
	 * @since 5.2
	 */
	public static final String MULTIPART_MIXED_VALUE = "";

	/**
	 * Public constant media type for {@code multipart/related}.
	 * @since 5.2.5
	 */
	public static final MediaType MULTIPART_RELATED;

	/**
	 * A String equivalent of {@link MediaType#MULTIPART_RELATED}.
	 * @since 5.2.5
	 */
	public static final String MULTIPART_RELATED_VALUE = "";

	/**
	 * Public constant media type for {@code text/event-stream}.
	 * @since 4.3.6
	 * @see <a href="https://www.w3.org/TR/eventsource/">Server-Sent Events W3C recommendation</a>
	 */
	public static final MediaType TEXT_EVENT_STREAM;

	/**
	 * A String equivalent of {@link MediaType#TEXT_EVENT_STREAM}.
	 * @since 4.3.6
	 */
	public static final String TEXT_EVENT_STREAM_VALUE = "";

	/**
	 * Public constant media type for {@code text/html}.
	 */
	public static final MediaType TEXT_HTML;

	/**
	 * A String equivalent of {@link MediaType#TEXT_HTML}.
	 */
	public static final String TEXT_HTML_VALUE = "";

	/**
	 * Public constant media type for {@code text/markdown}.
	 * @since 4.3
	 */
	public static final MediaType TEXT_MARKDOWN;

	/**
	 * A String equivalent of {@link MediaType#TEXT_MARKDOWN}.
	 * @since 4.3
	 */
	public static final String TEXT_MARKDOWN_VALUE = "";

	/**
	 * Public constant media type for {@code text/plain}.
	 */
	public static final MediaType TEXT_PLAIN;

	/**
	 * A String equivalent of {@link MediaType#TEXT_PLAIN}.
	 */
	public static final String TEXT_PLAIN_VALUE = "";

	/**
	 * Public constant media type for {@code text/xml}.
	 */
	public static final MediaType TEXT_XML;

	/**
	 * A String equivalent of {@link MediaType#TEXT_XML}.
	 */
	public static final String TEXT_XML_VALUE = "";

	private static final String PARAM_QUALITY_FACTOR = "";


	static {
		// Not using "valueOf' to avoid static init cost
		ALL = null;
		APPLICATION_ATOM_XML = null;
		APPLICATION_CBOR = null;
		APPLICATION_FORM_URLENCODED = null;
		APPLICATION_JSON = null;
		APPLICATION_JSON_UTF8 = null;
		APPLICATION_NDJSON = null;
		APPLICATION_OCTET_STREAM = null;
		APPLICATION_PDF = null;
		APPLICATION_PROBLEM_JSON = null;
		APPLICATION_PROBLEM_JSON_UTF8 = null;
		APPLICATION_PROBLEM_XML = null;
		APPLICATION_RSS_XML = null;
		APPLICATION_STREAM_JSON = null;
		APPLICATION_XHTML_XML = null;
		APPLICATION_XML = null;
		IMAGE_GIF = null;
		IMAGE_JPEG = null;
		IMAGE_PNG = null;
		MULTIPART_FORM_DATA = null;
		MULTIPART_MIXED = null;
		MULTIPART_RELATED = null;
		TEXT_EVENT_STREAM = null;
		TEXT_HTML = null;
		TEXT_MARKDOWN = null;
		TEXT_PLAIN = null;
		TEXT_XML = null;
	}


	/**
	 * Create a new {@code MediaType} for the given primary type.
	 * <p>The {@linkplain #getSubtype() subtype} is set to "&#42;", parameters empty.
	 * @param type the primary type
	 * @throws IllegalArgumentException if any of the parameters contain illegal characters
	 */
	public MediaType(String type) {
		super("");
	}

	/**
	 * Create a new {@code MediaType} for the given primary type and subtype.
	 * <p>The parameters are empty.
	 * @param type the primary type
	 * @param subtype the subtype
	 * @throws IllegalArgumentException if any of the parameters contain illegal characters
	 */
	public MediaType(String type, String subtype) {
		super("");
	}

	/**
	 * Create a new {@code MediaType} for the given type, subtype, and character set.
	 * @param type the primary type
	 * @param subtype the subtype
	 * @param charset the character set
	 * @throws IllegalArgumentException if any of the parameters contain illegal characters
	 */
	public MediaType(String type, String subtype, Charset charset) {
		super("");
	}

	/**
	 * Create a new {@code MediaType} for the given type, subtype, and quality value.
	 * @param type the primary type
	 * @param subtype the subtype
	 * @param qualityValue the quality value
	 * @throws IllegalArgumentException if any of the parameters contain illegal characters
	 */
	public MediaType(String type, String subtype, double qualityValue) {
		super("");
	}

	/**
	 * Copy-constructor that copies the type, subtype and parameters of the given
	 * {@code MediaType}, and allows to set the specified character set.
	 * @param other the other media type
	 * @param charset the character set
	 * @throws IllegalArgumentException if any of the parameters contain illegal characters
	 * @since 4.3
	 */
	public MediaType(MediaType other, Charset charset) {
		super("");
	}

	/**
	 * Copy-constructor that copies the type and subtype of the given {@code MediaType},
	 * and allows for different parameters.
	 * @param other the other media type
	 * @param parameters the parameters, may be {@code null}
	 * @throws IllegalArgumentException if any of the parameters contain illegal characters
	 */
	public MediaType(MediaType other, @Nullable Map<String, String> parameters) {
		super("");
	}

	/**
	 * Create a new {@code MediaType} for the given type, subtype, and parameters.
	 * @param type the primary type
	 * @param subtype the subtype
	 * @param parameters the parameters, may be {@code null}
	 * @throws IllegalArgumentException if any of the parameters contain illegal characters
	 */
	public MediaType(String type, String subtype, @Nullable Map<String, String> parameters) {
		super("");
	}

	/**
	 * Create a new {@code MediaType} for the given {@link MimeType}.
	 * The type, subtype and parameters information is copied and {@code MediaType}-specific
	 * checks on parameters are performed.
	 * @param mimeType the MIME type
	 * @throws IllegalArgumentException if any of the parameters contain illegal characters
	 * @since 5.3
	 */
	public MediaType(MimeType mimeType) {
		super("");
	}


	@Override
	protected void checkParameters(String parameter, String value) {
	}

	/**
	 * Return the quality factor, as indicated by a {@code q} parameter, if any.
	 * Defaults to {@code 1.0}.
	 * @return the quality factor as double value
	 */
	public double getQualityValue() {
		return 0.0;
	}

	/**
	 * Indicate whether this {@code MediaType} includes the given media type.
	 * <p>For instance, {@code text/*} includes {@code text/plain} and {@code text/html},
	 * and {@code application/*+xml} includes {@code application/soap+xml}, etc.
	 * This method is <b>not</b> symmetric.
	 * <p>Simply calls {@link MimeType#includes(MimeType)} but declared with a
	 * {@code MediaType} parameter for binary backwards compatibility.
	 * @param other the reference media type with which to compare
	 * @return {@code true} if this media type includes the given media type;
	 * {@code false} otherwise
	 */
	public boolean includes(@Nullable MediaType other) {
		return false;
	}

	/**
	 * Indicate whether this {@code MediaType} is compatible with the given media type.
	 * <p>For instance, {@code text/*} is compatible with {@code text/plain},
	 * {@code text/html}, and vice versa. In effect, this method is similar to
	 * {@link #includes}, except that it <b>is</b> symmetric.
	 * <p>Simply calls {@link MimeType#isCompatibleWith(MimeType)} but declared with a
	 * {@code MediaType} parameter for binary backwards compatibility.
	 * @param other the reference media type with which to compare
	 * @return {@code true} if this media type is compatible with the given media type;
	 * {@code false} otherwise
	 */
	public boolean isCompatibleWith(@Nullable MediaType other) {
		return false;
	}

	/**
	 * Return a replica of this instance with the quality value of the given {@code MediaType}.
	 * @return the same instance if the given MediaType doesn't have a quality value,
	 * or a new one otherwise
	 */
	public MediaType copyQualityValue(MediaType mediaType) {
		return null;
	}

	/**
	 * Return a replica of this instance with its quality value removed.
	 * @return the same instance if the media type doesn't contain a quality value,
	 * or a new one otherwise
	 */
	public MediaType removeQualityValue() {
		return null;
	}

	/**
	 * Parse the given String value into a {@code MediaType} object,
	 * with this method name following the 'valueOf' naming convention
	 * (as supported by {@link org.springframework.core.convert.ConversionService}.
	 * @param value the string to parse
	 * @throws InvalidMediaTypeException if the media type value cannot be parsed
	 * @see #parseMediaType(String)
	 */
	public static MediaType valueOf(String value) {
		return null;
	}

	/**
	 * Parse the given String into a single {@code MediaType}.
	 * @param mediaType the string to parse
	 * @return the media type
	 * @throws InvalidMediaTypeException if the media type value cannot be parsed
	 */
	public static MediaType parseMediaType(String mediaType) {
		return null;
	}

	/**
	 * Parse the comma-separated string into a list of {@code MediaType} objects.
	 * <p>This method can be used to parse an Accept or Content-Type header.
	 * @param mediaTypes the string to parse
	 * @return the list of media types
	 * @throws InvalidMediaTypeException if the media type value cannot be parsed
	 */
	public static List<MediaType> parseMediaTypes(@Nullable String mediaTypes) {
		return null;
	}

	/**
	 * Parse the given list of (potentially) comma-separated strings into a
	 * list of {@code MediaType} objects.
	 * <p>This method can be used to parse an Accept or Content-Type header.
	 * @param mediaTypes the string to parse
	 * @return the list of media types
	 * @throws InvalidMediaTypeException if the media type value cannot be parsed
	 * @since 4.3.2
	 */
	public static List<MediaType> parseMediaTypes(@Nullable List<String> mediaTypes) {
		return null;
	}

	/**
	 * Re-create the given mime types as media types.
	 * @since 5.0
	 */
	public static List<MediaType> asMediaTypes(List<MimeType> mimeTypes) {
		return null;
	}

	/**
	 * Re-create the given mime type as a media type.
	 * @since 5.0
	 */
	public static MediaType asMediaType(MimeType mimeType) {
		return null;
	}

	/**
	 * Return a string representation of the given list of {@code MediaType} objects.
	 * <p>This method can be used to for an {@code Accept} or {@code Content-Type} header.
	 * @param mediaTypes the media types to create a string representation for
	 * @return the string representation
	 */
	public static String toString(Collection<MediaType> mediaTypes) {
		return null;
	}

	/**
	 * Sorts the given list of {@code MediaType} objects by specificity.
	 * <p>Given two media types:
	 * <ol>
	 * <li>if either media type has a {@linkplain #isWildcardType() wildcard type}, then the media type without the
	 * wildcard is ordered before the other.</li>
	 * <li>if the two media types have different {@linkplain #getType() types}, then they are considered equal and
	 * remain their current order.</li>
	 * <li>if either media type has a {@linkplain #isWildcardSubtype() wildcard subtype}, then the media type without
	 * the wildcard is sorted before the other.</li>
	 * <li>if the two media types have different {@linkplain #getSubtype() subtypes}, then they are considered equal
	 * and remain their current order.</li>
	 * <li>if the two media types have different {@linkplain #getQualityValue() quality value}, then the media type
	 * with the highest quality value is ordered before the other.</li>
	 * <li>if the two media types have a different amount of {@linkplain #getParameter(String) parameters}, then the
	 * media type with the most parameters is ordered before the other.</li>
	 * </ol>
	 * <p>For example:
	 * <blockquote>audio/basic &lt; audio/* &lt; *&#047;*</blockquote>
	 * <blockquote>audio/* &lt; audio/*;q=0.7; audio/*;q=0.3</blockquote>
	 * <blockquote>audio/basic;level=1 &lt; audio/basic</blockquote>
	 * <blockquote>audio/basic == text/html</blockquote>
	 * <blockquote>audio/basic == audio/wave</blockquote>
	 * @param mediaTypes the list of media types to be sorted
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-5.3.2">HTTP 1.1: Semantics
	 * and Content, section 5.3.2</a>
	 */
	public static void sortBySpecificity(List<MediaType> mediaTypes) {
	}

	/**
	 * Sorts the given list of {@code MediaType} objects by quality value.
	 * <p>Given two media types:
	 * <ol>
	 * <li>if the two media types have different {@linkplain #getQualityValue() quality value}, then the media type
	 * with the highest quality value is ordered before the other.</li>
	 * <li>if either media type has a {@linkplain #isWildcardType() wildcard type}, then the media type without the
	 * wildcard is ordered before the other.</li>
	 * <li>if the two media types have different {@linkplain #getType() types}, then they are considered equal and
	 * remain their current order.</li>
	 * <li>if either media type has a {@linkplain #isWildcardSubtype() wildcard subtype}, then the media type without
	 * the wildcard is sorted before the other.</li>
	 * <li>if the two media types have different {@linkplain #getSubtype() subtypes}, then they are considered equal
	 * and remain their current order.</li>
	 * <li>if the two media types have a different amount of {@linkplain #getParameter(String) parameters}, then the
	 * media type with the most parameters is ordered before the other.</li>
	 * </ol>
	 * @param mediaTypes the list of media types to be sorted
	 * @see #getQualityValue()
	 */
	public static void sortByQualityValue(List<MediaType> mediaTypes) {
	}

	/**
	 * Sorts the given list of {@code MediaType} objects by specificity as the
	 * primary criteria and quality value the secondary.
	 * @see MediaType#sortBySpecificity(List)
	 * @see MediaType#sortByQualityValue(List)
	 */
	public static void sortBySpecificityAndQuality(List<MediaType> mediaTypes) {
	}

	/**
	 * Comparator used by {@link #sortByQualityValue(List)}.
	 */
	public static final Comparator<MediaType> QUALITY_VALUE_COMPARATOR = null;

	/**
	 * Comparator used by {@link #sortBySpecificity(List)}.
	 */
	public static final Comparator<MediaType> SPECIFICITY_COMPARATOR = null;
}
