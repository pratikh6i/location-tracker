/**
 * Google Apps Script for Traceract Location Tracking
 * 
 * Setup Instructions:
 * 1. Open your Google Sheet
 * 2. Go to Extensions > Apps Script
 * 3. Delete any existing code
 * 4. Paste this entire script
 * 5. Click Deploy > New deployment
 * 6. Select type: Web app
 * 7. Execute as: Me
 * 8. Who has access: Anyone
 * 9. Click Deploy
 * 10. Copy the Web app URL
 * 11. Paste it in the Traceract app settings
 */

function doPost(e) {
    try {
        // Get the active spreadsheet and first sheet
        const sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();

        // Parse the incoming JSON data
        const data = JSON.parse(e.postData.contents);

        // Log for debugging (visible in Executions log)
        Logger.log('Received data: ' + JSON.stringify(data));

        // Append a new row with the data
        // Column order: Device Name | Day | Date | Time | Latitude | Longitude
        sheet.appendRow([
            data.deviceName || 'Unknown',
            data.day || '',
            data.date || '',
            data.time || '',
            data.latitude || '',
            data.longitude || ''
        ]);

        // Return success response
        return ContentService
            .createTextOutput(JSON.stringify({
                success: true,
                message: 'Location saved successfully',
                timestamp: new Date().toISOString()
            }))
            .setMimeType(ContentService.MimeType.JSON);

    } catch (error) {
        // Return error response
        Logger.log('Error: ' + error.toString());
        return ContentService
            .createTextOutput(JSON.stringify({
                success: false,
                error: error.toString()
            }))
            .setMimeType(ContentService.MimeType.JSON);
    }
}

// Test function to verify the script works
function doGet(e) {
    return ContentService
        .createTextOutput('Traceract Location Tracker API is running. Use POST to submit data.')
        .setMimeType(ContentService.MimeType.TEXT);
}
