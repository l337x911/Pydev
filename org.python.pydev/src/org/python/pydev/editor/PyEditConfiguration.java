/*
 * Author: atotic
 * Created: July 10, 2003
 * License: Common Public License v1.0
 */
 
package org.python.pydev.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Preferences;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.DefaultUndoManager;
import org.eclipse.jface.text.IAutoIndentStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.IUndoManager;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWhitespaceDetector;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.python.pydev.editor.codecompletion.PyContentAssistant;
import org.python.pydev.editor.codecompletion.PythonCompletionProcessor;
import org.python.pydev.plugin.PydevPlugin;
import org.python.pydev.plugin.PydevPrefs;
import org.python.pydev.ui.ColorCache;


/**
 * Adds simple partitioner, and specific behaviors like double-click actions to the TextWidget.
 * 
 * <p>Implements a simple partitioner that does syntax highlighting.
 * 
 * 
 */
public class PyEditConfiguration extends SourceViewerConfiguration {

	private ColorCache colorCache;
	private PyAutoIndentStrategy autoIndentStrategy;
//	private ColorManager colorManager;
	private String[] indentPrefixes = { "    ", "\t", ""};
	private PyEdit edit;
	
	public PyEditConfiguration(ColorCache colorManager, PyEdit edit) {
		colorCache = colorManager;
		this.setEdit(edit);
	}

	public IUndoManager getUndoManager(ISourceViewer sourceViewer) {
		return new DefaultUndoManager(100);
	}

	/**
	 * Has to return all the types generated by partition scanner.
	 * 
	 * The SourceViewer will ignore double-clicks and any other configuration
	 * behaviors inside any partition not declared here
	 */
	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return new String[] { IDocument.DEFAULT_CONTENT_TYPE,
				PyPartitionScanner.PY_COMMENT,
				PyPartitionScanner.PY_SINGLELINE_STRING,
				PyPartitionScanner.PY_MULTILINE_STRING
				};
	}

	/**
	 * Cache the result, because we'll get asked for it multiple times
	 * Now, we always return the PyAutoIndentStrategy. (even on commented lines).
	 * @return PyAutoIndentStrategy which deals with spaces/tabs
	 */
	 public IAutoIndentStrategy getAutoIndentStrategy(ISourceViewer sourceViewer,String contentType) {
		if (autoIndentStrategy == null){
			autoIndentStrategy = new PyAutoIndentStrategy();
		}
		return autoIndentStrategy;
	 }
	
	 /**
	  * Recalculates indent prefixes based upon preferences
	  * 
	  * we hold onto the same array SourceViewer has, and write directly
	  * into it. This is because there is no way to tell SourceViewer that
	  * indent prefixes have changed. And we need this functionality when
	  * user resets the tabs vs. spaces preference
	  */
	public void resetIndentPrefixes() {
		Preferences prefs = PydevPlugin.getDefault().getPluginPreferences();
		int tabWidth = prefs.getInt(PydevPrefs.TAB_WIDTH);
		StringBuffer spaces = new StringBuffer(8);
		for (int i = 0; i < tabWidth; i++)
			spaces.append(" ");
		boolean spacesFirst = prefs.getBoolean(PydevPrefs.SUBSTITUTE_TABS) && 
				!((PyAutoIndentStrategy)getAutoIndentStrategy(null, null)).forceTabs;
		if (spacesFirst) {
			indentPrefixes[0] = spaces.toString();
			indentPrefixes[1] = "\t";
		}
		else {
			indentPrefixes[0] = "\t";
			indentPrefixes[1] = spaces.toString();
		}
	}
	
	/**
	 * Prefixes used in shift-left/shift-right editor operations
	 * 
	 * shift-right uses prefix[0]
	 * shift-left removes a single instance of the first prefix from the array that matches
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getIndentPrefixes(org.eclipse.jface.text.source.ISourceViewer, java.lang.String)
	 */
	public String[] getIndentPrefixes(
		ISourceViewer sourceViewer,
		String contentType) {
		resetIndentPrefixes();
		sourceViewer.setIndentPrefixes(indentPrefixes, contentType);
		return indentPrefixes;
	}

	 /**
	  * Just the default double-click strategy for now. But we should be smarter.
	  * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getDoubleClickStrategy(org.eclipse.jface.text.source.ISourceViewer, java.lang.String)
	  */
	 public ITextDoubleClickStrategy getDoubleClickStrategy(
	 		ISourceViewer sourceViewer,
			String contentType) {
	 	if (contentType.equals(IDocument.DEFAULT_CONTENT_TYPE))
	 		return new PyDoubleClickStrategy();
	 	else
		 	return super.getDoubleClickStrategy(sourceViewer, contentType);
	 }
	 
	/**
	 * TabWidth is defined inside pydev preferences.
	 * 
	 * Python uses its own tab width, since I think that its standard is 8
	 */
	public int getTabWidth(ISourceViewer sourceViewer) {
		return PydevPlugin.getDefault().getPluginPreferences().getInt(PydevPrefs.TAB_WIDTH);
	}
    
	/**
	 * @param color - default return color of this scanner
	 * @return scanner with no rules, that colors all text with default color
	 */
	private RuleBasedScanner getColoredScanner(Color color) {
		RuleBasedScanner scanner = new RuleBasedScanner();
		TextAttribute style = new TextAttribute(color);
		scanner.setDefaultReturnToken(new Token(style));
		return scanner;
	}
	
	/**
	 * Whitespace detector.
	 * 
	 * I know, naming the class after a band that burned
	 * is not funny, but I've got to get my brain off my
	 * annoyance with the abstractions of JFace.
	 * So many classes and interfaces for a single method?
	 * f$%@#$!!
	 */
	static private class GreatWhite implements IWhitespaceDetector {
		public boolean isWhitespace(char c) {return Character.isWhitespace(c);}
	};
	
	/**
	 * Python keyword detector
	 */
	static private class GreatKeywordDetector implements IWordDetector {
		// keywords list has to be alphabetized for this to work properly
		static public String[] keywords = {
				"and","as","assert","break","class","continue",
				"def","del","elif","else","except","exec",
				"finally","for","from","global",
				"if","import","in","is","lambda","not",
				"or","pass","print","raise","return",
				"try","while","yield","False", "None", "True" };

		public GreatKeywordDetector() {
		}
		public boolean isWordStart(char c) {
			return Character.isJavaIdentifierStart(c);
		}
		public boolean isWordPart(char c) {
			return Character.isJavaIdentifierPart(c);
		}
	}
	
	/** 
	 * Code scanner colors keywords
	 * 
	 */
	private RuleBasedScanner getCodeScanner() {
		RuleBasedScanner codeScanner = new RuleBasedScanner();
		IToken keywordToken = new Token(
				new TextAttribute(colorCache.getNamedColor(PydevPrefs.KEYWORD_COLOR)));
		IToken defaultToken = new Token(
				new TextAttribute(colorCache.getNamedColor("BLACK")));
		IToken errorToken = new Token(
				new TextAttribute(colorCache.getNamedColor("BLACK"))); // You can make it RED for fun display
		codeScanner.setDefaultReturnToken(errorToken);
		List rules = new ArrayList();
		
		// Scanning strategy:
		// 1) whitespace
		// 2) code
		// 3) regular words?
		
		rules.add(new WhitespaceRule(new GreatWhite()));
		
		WordRule wordRule = new WordRule(new GreatKeywordDetector(), defaultToken);
		for (int i=0; i<GreatKeywordDetector.keywords.length;i++) {
			wordRule.addWord(GreatKeywordDetector.keywords[i], keywordToken);
		}
		rules.add(wordRule);

		IRule[] result = new IRule[rules.size()];
		rules.toArray(result);
		codeScanner.setRules(result);
		return codeScanner;
	}
 
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {

		PresentationReconciler reconciler = new PresentationReconciler();

		DefaultDamagerRepairer dr;

		// DefaultDamagerRepairer implements both IPresentationDamager, IPresentationRepairer 
		// IPresentationDamager::getDamageRegion does not scan, just 
		// returns the intersection of document event, and partition region
		// IPresentationRepairer::createPresentation scans
		// gets each token, and sets text attributes according to token
		
		// We need to cover all the content types from PyPartitionScanner

		// Comments have uniform color
		dr = new DefaultDamagerRepairer(
				getColoredScanner((colorCache.getNamedColor(PydevPrefs.COMMENT_COLOR))));
		reconciler.setDamager(dr, PyPartitionScanner.PY_COMMENT);
		reconciler.setRepairer(dr, PyPartitionScanner.PY_COMMENT);

		// Strings have uniform color
		dr = new DefaultDamagerRepairer(
				getColoredScanner((colorCache.getNamedColor(PydevPrefs.STRING_COLOR))));
		reconciler.setDamager(dr, PyPartitionScanner.PY_SINGLELINE_STRING);
		reconciler.setRepairer(dr, PyPartitionScanner.PY_SINGLELINE_STRING);
		reconciler.setDamager(dr, PyPartitionScanner.PY_MULTILINE_STRING);
		reconciler.setRepairer(dr, PyPartitionScanner.PY_MULTILINE_STRING);
	
		// Default content is code, we need syntax highlighting
		dr = new DefaultDamagerRepairer(
			getCodeScanner());
		reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);

		return reconciler;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getContentAssistant(org.eclipse.jface.text.source.ISourceViewer)
	 */
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
 		final String   PY_SINGLELINE_STRING = "__python_singleline_string";
		final String   PY_MULTILINE_STRING = "__python_multiline_string";
		
		// create a content assistant:
		ContentAssistant assistant = new PyContentAssistant();
		
		// next create a content assistant processor to populate the completions window
		IContentAssistProcessor processor = new PythonCompletionProcessor(this.getEdit());
		
		// No code completion in strings
		assistant.setContentAssistProcessor(processor,PyPartitionScanner.PY_SINGLELINE_STRING );
 		assistant.setContentAssistProcessor(processor,PyPartitionScanner.PY_MULTILINE_STRING );
		assistant.setContentAssistProcessor(processor,IDocument.DEFAULT_CONTENT_TYPE );
		assistant.setInformationControlCreator(getInformationControlCreator(sourceViewer));
		
		//delay and auto activate set on PyContentAssistant constructor.
		
		Color bgColor = colorCache.getColor(new RGB(230,255,230));
		assistant.setProposalSelectorBackground(bgColor);
		return assistant;
	}


	// The presenter instance for the information window
	private static final DefaultInformationControl.IInformationPresenter presenter =
		new DefaultInformationControl.IInformationPresenter() {
		public String updatePresentation(
			Display display,
			String infoText,
			TextPresentation presentation,
			int maxWidth,
			int maxHeight) {
			int start = -1;
			// Loop over all characters of information text
			//These will have to be tailored for the appropriate Python
			for (int i = 0; i < infoText.length(); i++) {
				switch (infoText.charAt(i)) {
					case '<' :
						// Remember start of tag
						start = i;
						break;
					case '>' :
						if (start >= 0) {
							// We have found a tag and create a new style range
							StyleRange range =
								new StyleRange(
									start,
									i - start + 1,
									null,
									null,
									SWT.BOLD);
							// Add this style range to the presentation
							presentation.addStyleRange(range);
							// Reset tag start indicator
							start = -1;
						}
						break;
				}
			}
			// Return the information text
			return infoText;
		}
	};

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getInformationControlCreator(org.eclipse.jface.text.source.ISourceViewer)
	 */
	public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
		return new IInformationControlCreator() {
			public IInformationControl createInformationControl(Shell parent) {
				return new DefaultInformationControl(parent, presenter);
			}
		};
	}

    /**
     * @param edit The edit to set.
     */
    private void setEdit(PyEdit edit) {
        this.edit = edit;
    }

    /**
     * @return Returns the edit.
     */
    private PyEdit getEdit() {
        return edit;
    }




}