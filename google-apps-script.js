/**
 * Google Apps Script for Traceract - Updated with Battery & Carrier
 * 
 * Setup Instructions:
 * 1. Open: https://docs.google.com/spreadsheets/d/1zM47CKF5q3bAzTBylajNPvuz-9YSLvIw-8djtZtwXro/edit
 * 2. Extensions > Apps Script
 * 3. Delete existing code
 * 4. Paste this script
 * 5. Deploy > New deployment > Web app
 * 6. Execute as: Me
 * 7. Who has access: Anyone
 * 8. Copy Web App URL
 */

function doPost(e) {
    try {
        // Use lock to prevent concurrent writes
        const lock = LockService.getScriptLock();
        lock.waitLock(30000);

        const sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
        const data = JSON.parse(e.postData.contents);

        // Validate required fields
        if (!data.deviceName || !data.latitude || !data.longitude) {
            throw new Error('Missing required fields: deviceName, latitude, or longitude');
        }

        // Log for debugging
        Logger.log('Received: ' + JSON.stringify(data));

        // Column order: Device Name | Day | Date | Time | Latitude | Longitude | Battery | Carrier
        sheet.appendRow([
            data.deviceName || 'Unknown',
            data.day || new Date().toLocaleDateString('en-US', { weekday: 'long' }),
            data.date || new Date().toLocaleDateString('en-IN'),
            data.time || new Date().toLocaleTimeString('en-IN'),
            data.latitude,
            data.longitude,
            data.battery !== undefined ? data.battery + '%' : 'N/A',
            data.carrier || 'Unknown'
        ]);

        lock.releaseLock();

        return ContentService
            .createTextOutput(JSON.stringify({
                success: true,
                message: 'Location saved successfully',
                timestamp: new Date().toISOString()
            }))
            .setMimeType(ContentService.MimeType.JSON);

    } catch (error) {
        Logger.log('ERROR: ' + error.toString());
        return ContentService
            .createTextOutput(JSON.stringify({
                success: false,
                error: error.toString()
            }))
            .setMimeType(ContentService.MimeType.JSON);
    }
}

// Test endpoint
function doGet(e) {
    return ContentService
        .createTextOutput('Traceract Location Tracker API is running. Use POST to submit data.')
        .setMimeType(ContentService.MimeType.TEXT);
}
