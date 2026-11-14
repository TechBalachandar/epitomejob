package com.epitomejob.resumebuilder;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.LittleEndian;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Shared resume fixtures for the builder tests, built in-test with
 * PDFBox / POI (no binary fixtures in the tree). Used by both
 * ResumeParseServiceTest and ResumeBuilderJourneyIT — the fixtures live
 * here (public) instead of on the package-private test class, so the
 * journey IT in this package can use them.
 */
public final class TestFixtures {

  private TestFixtures() {
  }

  /** Tiny PDF fixture, built in-test with PDFBox (no binary fixtures in the tree). */
  public static byte[] pdfFixture() throws Exception {
    try (PDDocument doc = new PDDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
        // PDFBox 3: PDType1Font.HELVETICA is gone; use Standard14Fonts.
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.beginText();
        stream.newLineAtOffset(50, 700);
        for (String line : new String[]{
            "Ada Lovelace", "ada@example.com", "SUMMARY",
            "Analytical engine pioneer.", "EXPERIENCE",
            "Senior Engineer at Analytical Engines Ltd Jan 2020 – Present",
            "• Designed the first algorithm"}) {
          stream.showText(line);
          stream.newLineAtOffset(0, -16);
        }
        stream.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  /** Tiny DOCX fixture, built in-test with POI XWPF. */
  public static byte[] docxFixture() throws Exception {
    try (XWPFDocument doc = new XWPFDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (String line : new String[]{"Ada Lovelace", "ada@example.com", "SUMMARY",
          "Analytical engine pioneer.", "SKILLS", "Java, Spring Boot"}) {
        XWPFParagraph paragraph = doc.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(line);
      }
      doc.write(out);
      return out.toByteArray();
    }
  }

  /**
   * Minimal legacy DOC fixture: a valid, empty Word 97 document, built
   * in-test (no binary fixtures in the tree). POI's HWPFDocument has no
   * no-arg constructor, so the WordDocument stream is a hand-built 900-byte
   * FIB and the 0Table stream holds the structures HWPF's constructor
   * reads: a 500-byte DocumentProperties (DOP), a minimal 20-byte
   * stylesheet (cbStshi=18, cstd=0), a 2-byte empty font table, and a
   * 21-byte Clx holding a Pcdt with one empty text piece (CP 0..0,
   * fc=fcMin=900, prm=0). Layout (POI FileInformationBlock /
   * FIBFieldHandler, nFib 0x00C1): FibBase 32 bytes, csw=14, FibRgW97
   * 28 bytes, cslw=22, FibRgLw97 88 bytes, cbRgFcLcb=93, FibRgFcLcb
   * 744 bytes, cswNew=0. Every other field is zero, so the document has
   * no text — HWPF reads it as an empty document and the parsed text is
   * blank.
   */
  public static byte[] docFixture() throws Exception {
    byte[] fib = new byte[900];
    LittleEndian.putShort(fib, 0, (short) 0xA5EC);   // wIdent
    LittleEndian.putShort(fib, 2, (short) 0x00C1);   // nFib (Word 97)
    LittleEndian.putShort(fib, 6, (short) 0x0409);   // lid (en-US)
    LittleEndian.putShort(fib, 12, (short) 0x00C1);  // nFibBack
    LittleEndian.putShort(fib, 32, (short) 14);       // csw
    LittleEndian.putShort(fib, 62, (short) 22);       // cslw
    LittleEndian.putInt(fib, 64, 900);                // fcMin (FibRgLw97[0])
    LittleEndian.putShort(fib, 152, (short) 93);      // cbRgFcLcb (0x5D)
    LittleEndian.putShort(fib, 898, (short) 0);       // cswNew
    // FibRgFcLcb fields (field N: fc at 154 + N*8, lcb at +4).
    fibField(fib, 31, 0, 500);    // DOP (DocumentProperties, 500 bytes)
    fibField(fib, 1, 500, 20);    // STSHF (stylesheet)
    fibField(fib, 15, 520, 2);    // STTBFFFN (font table: 0 fonts)
    fibField(fib, 33, 522, 21);   // CLX (piece table)
    // Table stream: DOP (500 zero bytes) + stylesheet + font table + Clx.
    byte[] table = new byte[543];
    LittleEndian.putShort(table, 500, (short) 18); // cbStshi = 18 (Stshi size, cstd = 0)
    // Clx (Pcdt): clxt=0x02, lcb=16, PlcPcd: CP[0]=0, CP[1]=0, Pcd{fc=900, prm=0}.
    table[522] = 0x02;
    LittleEndian.putInt(table, 523, 16);
    LittleEndian.putInt(table, 527, 0);   // CP[0]
    LittleEndian.putInt(table, 531, 0);   // CP[1]
    LittleEndian.putInt(table, 535, 900); // Pcd fc (fcMin)
    LittleEndian.putInt(table, 539, 0);   // Pcd prm (istd 0, not complex)
    try (POIFSFileSystem fs = new POIFSFileSystem();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      fs.getRoot().createDocument("WordDocument", new ByteArrayInputStream(fib));
      fs.getRoot().createDocument("0Table", new ByteArrayInputStream(table));
      fs.writeFilesystem(out);
      return out.toByteArray();
    }
  }

  private static void fibField(byte[] fib, int field, int fc, int lcb) {
    LittleEndian.putInt(fib, 154 + field * 8, fc);
    LittleEndian.putInt(fib, 154 + field * 8 + 4, lcb);
  }
}
