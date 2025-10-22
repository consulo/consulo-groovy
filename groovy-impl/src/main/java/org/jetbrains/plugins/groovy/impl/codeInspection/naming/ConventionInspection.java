/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.impl.codeInspection.naming;

import consulo.ide.impl.idea.util.ui.RegExFormatter;
import consulo.ide.impl.idea.util.ui.RegExInputVerifier;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.event.DocumentAdapter;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.InternationalFormatter;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ConventionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return LocalizeValue.localizeTODO("Naming Conventions");
    }

    /**
     * public fields for the DefaultJDomExternalizer
     *
     * @noinspection PublicField, WeakerAccess
     */
    public String myRegex = getDefaultRegex();
    /**
     * @noinspection PublicField, WeakerAccess
     */
    public int myMinLength = getDefaultMinLength();
    /**
     * @noinspection PublicField, WeakerAccess
     */
    public int myMaxLength = getDefaultMaxLength();

    protected Pattern myRegexPattern = Pattern.compile(myRegex);

    protected abstract String getDefaultRegex();

    protected abstract int getDefaultMinLength();

    protected abstract int getDefaultMaxLength();

    protected String getRegex() {
        return myRegex;
    }

    protected int getMinLength() {
        return myMinLength;
    }

    protected int getMaxLength() {
        return myMaxLength;
    }

    protected boolean isValid(String name) {
        int length = name.length();
        if (length < myMinLength) {
            return false;
        }
        if (length > myMaxLength) {
            return false;
        }
        if ("SerialVersionUID".equals(name)) {
            return true;
        }
        Matcher matcher = myRegexPattern.matcher(name);
        return matcher.matches();
    }

    private static final int REGEX_COLUMN_COUNT = 25;

    public JComponent createOptionsPanel() {
        GridBagLayout layout = new GridBagLayout();
        JPanel panel = new JPanel(layout);

        JLabel patternLabel = new JLabel("Pattern:");
        JLabel minLengthLabel = new JLabel("Min Length:");
        JLabel maxLengthLabel = new JLabel("Max Length:");

        NumberFormat numberFormat = NumberFormat.getIntegerInstance();
        numberFormat.setParseIntegerOnly(true);
        numberFormat.setMinimumIntegerDigits(1);
        numberFormat.setMaximumIntegerDigits(2);
        InternationalFormatter formatter = new InternationalFormatter(numberFormat);
        formatter.setAllowsInvalid(false);
        formatter.setCommitsOnValidEdit(true);

        final JFormattedTextField minLengthField = new JFormattedTextField(formatter);
        Font panelFont = panel.getFont();
        minLengthField.setFont(panelFont);
        minLengthField.setValue(myMinLength);
        minLengthField.setColumns(2);
        UIUtil.fixFormattedField(minLengthField);

        final JFormattedTextField maxLengthField = new JFormattedTextField(formatter);
        maxLengthField.setFont(panelFont);
        maxLengthField.setValue(myMaxLength);
        maxLengthField.setColumns(2);
        UIUtil.fixFormattedField(maxLengthField);

        final JFormattedTextField regexField = new JFormattedTextField(new RegExFormatter());
        regexField.setFont(panelFont);
        regexField.setValue(myRegexPattern);
        regexField.setColumns(REGEX_COLUMN_COUNT);
        regexField.setInputVerifier(new RegExInputVerifier());
        regexField.setFocusLostBehavior(JFormattedTextField.COMMIT);
        UIUtil.fixFormattedField(regexField);
        DocumentListener listener = new DocumentAdapter() {
            @Override
            public void textChanged(DocumentEvent evt) {
                try {
                    regexField.commitEdit();
                    minLengthField.commitEdit();
                    maxLengthField.commitEdit();
                    myRegexPattern = (Pattern) regexField.getValue();
                    myRegex = myRegexPattern.pattern();
                    myMinLength = ((Number) minLengthField.getValue()).intValue();
                    myMaxLength = ((Number) maxLengthField.getValue()).intValue();
                }
                catch (ParseException ignore) {
                    // No luck this time
                }
            }
        };
        Document regexDocument = regexField.getDocument();
        regexDocument.addDocumentListener(listener);
        Document minLengthDocument = minLengthField.getDocument();
        minLengthDocument.addDocumentListener(listener);
        Document maxLengthDocument = maxLengthField.getDocument();
        maxLengthDocument.addDocumentListener(listener);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0.0;
        constraints.insets.right = UIUtil.DEFAULT_HGAP;
        constraints.anchor = GridBagConstraints.BASELINE_LEADING;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(patternLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.insets.right = 0;
        panel.add(regexField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0.0;
        constraints.insets.right = UIUtil.DEFAULT_HGAP;
        panel.add(minLengthLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.weightx = 1;
        constraints.insets.right = 0;
        panel.add(minLengthField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 0;
        constraints.insets.right = UIUtil.DEFAULT_HGAP;
        panel.add(maxLengthLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 2;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.insets.right = 0;
        panel.add(maxLengthField, constraints);

        return panel;
    }
}