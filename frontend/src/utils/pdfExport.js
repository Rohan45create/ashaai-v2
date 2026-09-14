import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';

/**
 * Direct client-side PDF export pipeline using bundled jsPDF and autoTable.
 * 
 * Implements Path B (Tier 1: Autonomous Report Export per docs/ARCHITECTURE.md):
 * - Read-only export of existing submitted health records.
 * - Skips review gate; immediately downloads the formatted PDF.
 *
 * @param {Object} options
 * @param {string} options.title - Report title (e.g. 'Immunization Report')
 * @param {string} [options.subtitle] - Worker / Period subtitle
 * @param {Array<string>} options.headers - Column header labels
 * @param {Array<Array<any>>} options.rows - 2D matrix of row data
 * @param {string} [options.filename] - Target PDF filename
 * @param {boolean} [options.asBlob] - If true, returns Blob instead of saving directly
 * @returns {Promise<{success: boolean, filename: string, blob?: Blob}>}
 */
export async function exportReportPdf({
  title = 'AshaAI Report',
  subtitle = '',
  headers = [],
  rows = [],
  filename,
  asBlob = false
}) {
  try {
    const doc = new jsPDF({ orientation: 'landscape' });
    const timestamp = new Date().toISOString().split('T')[0];
    const targetFilename = filename || `AshaAI_${title.replace(/[^a-zA-Z0-9]/g, '_')}_${timestamp}.pdf`;

    // Report Header
    doc.setFontSize(18);
    doc.setTextColor(8, 80, 65); // AshaAI Primary Green
    doc.text(title, 14, 20);

    doc.setFontSize(10);
    doc.setTextColor(100);
    if (subtitle) {
      doc.text(subtitle, 14, 28);
      doc.text(`Generated on: ${new Date().toLocaleString()}`, 14, 34);
    } else {
      doc.text(`Generated on: ${new Date().toLocaleString()}`, 14, 28);
    }

    const startY = subtitle ? 40 : 34;

    if (!rows || rows.length === 0) {
      doc.setFontSize(12);
      doc.setTextColor(150, 0, 0);
      doc.text('No submitted records found for the requested criteria.', 14, startY + 8);
    } else {
      doc.setFontSize(11);
      doc.setTextColor(20);
      doc.text(`Total Records: ${rows.length}`, 14, startY);

      autoTable(doc, {
        startY: startY + 4,
        head: [headers],
        body: rows,
        theme: 'striped',
        headStyles: {
          fillColor: [29, 158, 117],
          fontSize: 9,
          textColor: [255, 255, 255],
          fontStyle: 'bold'
        },
        bodyStyles: {
          fontSize: 9,
          cellPadding: 3
        },
        alternateRowStyles: {
          fillColor: [245, 247, 246]
        },
        margin: { left: 14, right: 14 }
      });
    }

    if (asBlob) {
      const blob = doc.output('blob');
      return { success: true, filename: targetFilename, blob };
    }

    doc.save(targetFilename);
    return { success: true, filename: targetFilename };
  } catch (err) {
    console.error('[pdfExport] Failed to generate PDF:', err);
    throw err;
  }
}
