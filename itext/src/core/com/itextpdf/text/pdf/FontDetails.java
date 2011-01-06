/*
 * $Id$
 *
 * This file is part of the iText project.
 * Copyright (c) 1998-2009 1T3XT BVBA
 * Authors: Bruno Lowagie, Paulo Soares, et al.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY 1T3XT,
 * 1T3XT DISCLAIMS THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://itextpdf.com/terms-of-use/
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * In accordance with Section 7(b) of the GNU Affero General Public License,
 * you must retain the producer line in every PDF that is created or manipulated
 * using iText.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the iText software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers as an ASP,
 * serving PDFs on the fly in a web application, shipping iText with a closed
 * source product.
 *
 * For more information, please contact iText Software Corp. at this
 * address: sales@itextpdf.com
 */
package com.itextpdf.text.pdf;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import com.itextpdf.text.ExceptionConverter;
import com.itextpdf.text.Utilities;

/**
 * Each font in the document will have an instance of this class
 * where the characters used will be represented.
 *
 * @author  Paulo Soares
 */
class FontDetails {

    /**
     * The indirect reference to this font
     */
    PdfIndirectReference indirectReference;
    /**
     * The font name that appears in the document body stream
     */
    PdfName fontName;
    /**
     * The font
     */
    BaseFont baseFont;
    /**
     * The font if it's an instance of <CODE>TrueTypeFontUnicode</CODE>
     */
    TrueTypeFontUnicode ttu;
    /**
     * The font if it's an instance of <CODE>CJKFont</CODE>
     */
    CJKFont cjkFont;
    /**
     * The array used with single byte encodings
     */
    byte shortTag[];
    /**
     * The map used with double byte encodings. The key is Integer(glyph) and
     * the value is int[]{glyph, width, Unicode code}
     */
    HashMap<Integer, int[]> longTag;
    /**
     * IntHashtable with CIDs of CJK glyphs that are used in the text.
     */
    IntHashtable cjkTag;
    /**
     * The font type
     */
    int fontType;
    /**
     * <CODE>true</CODE> if the font is symbolic
     */
    boolean symbolic;
    /**
     * Indicates if only a subset of the glyphs and widths for that particular
     * encoding should be included in the document.
     */
    protected boolean subset = true;

    /**
     * Each font used in a document has an instance of this class.
     * This class stores the characters used in the document and other
     * specifics unique to the current working document.
     * @param fontName the font name
     * @param indirectReference the indirect reference to the font
     * @param baseFont the <CODE>BaseFont</CODE>
     */
    FontDetails(PdfName fontName, PdfIndirectReference indirectReference, BaseFont baseFont) {
        this.fontName = fontName;
        this.indirectReference = indirectReference;
        this.baseFont = baseFont;
        fontType = baseFont.getFontType();
        switch (fontType) {
            case BaseFont.FONT_TYPE_T1:
            case BaseFont.FONT_TYPE_TT:
                shortTag = new byte[256];
                break;
            case BaseFont.FONT_TYPE_CJK:
                cjkTag = new IntHashtable();
                cjkFont = (CJKFont)baseFont;
                break;
            case BaseFont.FONT_TYPE_TTUNI:
                longTag = new HashMap<Integer, int[]>();
                ttu = (TrueTypeFontUnicode)baseFont;
                symbolic = baseFont.isFontSpecific();
                break;
        }
    }

    /**
     * Gets the indirect reference to this font.
     * @return the indirect reference to this font
     */
    PdfIndirectReference getIndirectReference() {
        return indirectReference;
    }

    /**
     * Gets the font name as it appears in the document body.
     * @return the font name
     */
    PdfName getFontName() {
        return fontName;
    }

    /**
     * Gets the <CODE>BaseFont</CODE> of this font.
     * @return the <CODE>BaseFont</CODE> of this font
     */
    BaseFont getBaseFont() {
        return baseFont;
    }

    /**
     * Converts the text into bytes to be placed in the document.
     * The conversion is done according to the font and the encoding and the characters
     * used are stored.
     * @param text the text to convert
     * @return the conversion
     */
    byte[] convertToBytes(String text) {
        byte b[] = null;
        switch (fontType) {
            case BaseFont.FONT_TYPE_T3:
                return baseFont.convertToBytes(text);
            case BaseFont.FONT_TYPE_T1:
            case BaseFont.FONT_TYPE_TT: {
                b = baseFont.convertToBytes(text);
                int len = b.length;
                for (int k = 0; k < len; ++k)
                    shortTag[b[k] & 0xff] = 1;
                break;
            }
            case BaseFont.FONT_TYPE_CJK: {
                int len = text.length();
                for (int k = 0; k < len; ++k)
                    cjkTag.put(cjkFont.getCidCode(text.charAt(k)), 0);
                b = baseFont.convertToBytes(text);
                break;
            }
            case BaseFont.FONT_TYPE_DOCUMENT: {
                b = baseFont.convertToBytes(text);
                break;
            }
            case BaseFont.FONT_TYPE_TTUNI: {
                try {
                    int len = text.length();
                    int metrics[] = null;
                    char glyph[] = new char[len];
                    int i = 0;
                    if (symbolic) {
                        b = PdfEncodings.convertToBytes(text, "symboltt");
                        len = b.length;
                        for (int k = 0; k < len; ++k) {
                            metrics = ttu.getMetricsTT(b[k] & 0xff);
                            if (metrics == null)
                                continue;
                            longTag.put(Integer.valueOf(metrics[0]), new int[]{metrics[0], metrics[1], ttu.getUnicodeDifferences(b[k] & 0xff)});
                            glyph[i++] = (char)metrics[0];
                        }
                    }
                    else {
                    	for (int k = 0; k < len; ++k) {
                    		int val;
                    		if (Utilities.isSurrogatePair(text, k)) {
                    			val = Utilities.convertToUtf32(text, k);
                    			k++;
                    		}
                    		else {
                    			val = text.charAt(k);
                    		}
                    		metrics = ttu.getMetricsTT(val);
                    		if (metrics == null)
                    			continue;
                    		int m0 = metrics[0];
                    		Integer gl = Integer.valueOf(m0);
                    		if (!longTag.containsKey(gl))
                    			longTag.put(gl, new int[]{m0, metrics[1], val});
                    		glyph[i++] = (char)m0;
                    	}
                    }
                    String s = new String(glyph, 0, i);
                    b = s.getBytes(CJKFont.CJK_ENCODING);
                }
                catch (UnsupportedEncodingException e) {
                    throw new ExceptionConverter(e);
                }
                break;
            }
        }
        return b;
    }

    /**
     * Writes the font definition to the document.
     * @param writer the <CODE>PdfWriter</CODE> of this document
     */
    void writeFont(PdfWriter writer) {
        try {
            switch (fontType) {
                case BaseFont.FONT_TYPE_T3:
                    baseFont.writeFont(writer, indirectReference, null);
                    break;
                case BaseFont.FONT_TYPE_T1:
                case BaseFont.FONT_TYPE_TT: {
                    int firstChar;
                    int lastChar;
                    for (firstChar = 0; firstChar < 256; ++firstChar) {
                        if (shortTag[firstChar] != 0)
                            break;
                    }
                    for (lastChar = 255; lastChar >= firstChar; --lastChar) {
                        if (shortTag[lastChar] != 0)
                            break;
                    }
                    if (firstChar > 255) {
                        firstChar = 255;
                        lastChar = 255;
                    }
                    baseFont.writeFont(writer, indirectReference, new Object[]{Integer.valueOf(firstChar), Integer.valueOf(lastChar), shortTag, Boolean.valueOf(subset)});
                    break;
                }
                case BaseFont.FONT_TYPE_CJK:
                    baseFont.writeFont(writer, indirectReference, new Object[]{cjkTag});
                    break;
                case BaseFont.FONT_TYPE_TTUNI:
                    baseFont.writeFont(writer, indirectReference, new Object[]{longTag, Boolean.valueOf(subset)});
                    break;
            }
        }
        catch(Exception e) {
            throw new ExceptionConverter(e);
        }
    }

    /**
     * Indicates if all the glyphs and widths for that particular
     * encoding should be included in the document.
     * @return <CODE>false</CODE> to include all the glyphs and widths.
     */
    public boolean isSubset() {
        return subset;
    }

    /**
     * Indicates if all the glyphs and widths for that particular
     * encoding should be included in the document. Set to <CODE>false</CODE>
     * to include all.
     * @param subset new value of property subset
     */
    public void setSubset(boolean subset) {
        this.subset = subset;
    }
}
