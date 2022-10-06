/*
 * Copyright 2022 the original author or authors.
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

package io.micrometer.docs.commons.utils;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Javadoc;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.TagElement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.TextElement;
import org.jboss.forge.roaster.model.source.JavaDocSource;

/**
 * Utilities to parse javadoc fragments in various form (String, modelling objects) to asciidoc strings.
 */
public class AsciidocUtils {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AsciidocUtils.class);

    private static final String NEWLINE = System.lineSeparator();
    private static final String LINE_BREAK = " +" + NEWLINE;
    private static final String PARAGRAPH_BREAK = NEWLINE + NEWLINE;

    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\R");

    public static final String simpleHtmlToAsciidoc(String line, boolean assumeLiOrdered) {
        String asciidoc = line
                .replaceAll("<p/?>", PARAGRAPH_BREAK)
                .replaceAll("<br/?>", LINE_BREAK)
                .replaceAll("<strong>", PARAGRAPH_BREAK + "IMPORTANT: ")
                .replaceAll("</strong>\\h+", PARAGRAPH_BREAK)
                .replaceAll("</?b>", "*")
                .replaceAll("</?i>", "_")
                .replaceAll("<ul>", NEWLINE)
                .replaceAll("<ol>", NEWLINE)
                .replaceAll("</[uo]l>", NEWLINE)
                .replaceAll("<li>", NEWLINE + (assumeLiOrdered ? " 1. " : " - "));
        //strip all other tags (closing tags, unknown tags)
        return asciidoc.replaceAll("<[^<>]*>", "");
    }

    public static final String simpleTagletToAsciidoc(String tagletName, List<?> tagletFragments) {
        if ("@code".equals(tagletName) || "@value".equals(tagletName)) {
            return tagletFragments
                    .stream()
                    .map(o -> o.toString().trim())
                    .collect(Collectors.joining(" ", "`", "`"));
        }
        if ("@link".equals(tagletName) || "@linkplain".equals(tagletName)) {
            Stream<String> stream = tagletFragments
                    .stream()
                    .map(o -> o.toString().trim());

            if (tagletFragments.size() > 1)
                return stream
                        .skip(1)
                        .collect(Collectors.joining(" "));
            return stream
                    .collect(Collectors.joining(" ", "`", "`"));
        }
        //render the full taglet as an inline code block
        return Stream.concat(
                        Stream.of(tagletName),
                        tagletFragments.stream().map(o -> o.toString().trim())
                )
                .collect(Collectors.joining(" ", "`{", "}`"));
    }

    public static final String javadocToAsciidoc(JavaDocSource<?> javadoc) {
        Object internal = javadoc.getInternal();
        if (!(internal instanceof Javadoc)) {
            return javadoc.getText();
        }
        Javadoc internalJavadoc = (Javadoc) internal;
        @SuppressWarnings("unchecked")
        List<TagElement> tagList = internalJavadoc.tags();
        StringBuilder text = new StringBuilder();

        boolean openedOrderedList = false;
        for (TagElement tagElement : tagList) {
            //only consider the javadoc description
            if (tagElement.getTagName() != null)
                continue;
            for (Object fragment: tagElement.fragments()) {
                //ignored: SimpleName
                if (fragment instanceof TextElement) {
                    TextElement textElement = (TextElement) fragment;
                    String line = textElement.getText();
                    //inline taglets will be separate fragments. we only care for embedded HTML subset
                    if (line.contains("<") && line.contains(">")) {
                        //only reset the li type when explicitly encountering an ol or ul.
                        //note ol takes precedence, and this doesn't really work with nested ol/ul combinations.
                        if (line.contains("<ul>")) {
                            openedOrderedList = false;
                        }
                        if (line.contains("<ol>")) {
                            openedOrderedList = true;
                        }

                        text.append(simpleHtmlToAsciidoc(line, openedOrderedList));
                    }
                    else {
                        //we append a space at the end so that javadoc linebreaks in the middle of a simple text translate to a space
                        text.append(line).append(' ');
                    }
                }
                else if (fragment instanceof TagElement) {
                    TagElement tagFragment = (TagElement) fragment;
                    text.append(simpleTagletToAsciidoc(tagFragment.getTagName(), tagFragment.fragments()));
                }
                else {
                    LOGGER.debug("dropped fragment during javadoc to asciidoc parsing: %s", tagElement);
                }
            }
        }
        //second pass on each line to trim undesirable spaces
        String trimmed = NEWLINE_PATTERN
                .splitAsStream(text)
                .map(line -> line
                        // we don't want multiple spaces in a row
                        .replaceAll("\\h\\h+", " ")
                        //we don't want trailing whitespaces, trim() doesn't work because we do want leading space when relevant
                        .replaceAll("\\h+$", ""))
                .collect(Collectors.joining(System.lineSeparator()));
        return trimmed;
    }
}
