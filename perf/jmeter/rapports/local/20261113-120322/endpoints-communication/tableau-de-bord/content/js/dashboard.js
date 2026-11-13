/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
var showControllersOnly = false;
var seriesFilter = "";
var filtersOnlySampleSeries = true;

/*
 * Add header in statistics table to group metrics by category
 * format
 *
 */
function summaryTableHeader(header) {
    var newRow = header.insertRow(-1);
    newRow.className = "tablesorter-no-sort";
    var cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Requests";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 3;
    cell.innerHTML = "Executions";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 7;
    cell.innerHTML = "Response Times (ms)";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Throughput";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 2;
    cell.innerHTML = "Network (KB/sec)";
    newRow.appendChild(cell);
}

/*
 * Populates the table identified by id parameter with the specified data and
 * format
 *
 */
function createTable(table, info, formatter, defaultSorts, seriesIndex, headerCreator) {
    var tableRef = table[0];

    // Create header and populate it with data.titles array
    var header = tableRef.createTHead();

    // Call callback is available
    if(headerCreator) {
        headerCreator(header);
    }

    var newRow = header.insertRow(-1);
    for (var index = 0; index < info.titles.length; index++) {
        var cell = document.createElement('th');
        cell.innerHTML = info.titles[index];
        newRow.appendChild(cell);
    }

    var tBody;

    // Create overall body if defined
    if(info.overall){
        tBody = document.createElement('tbody');
        tBody.className = "tablesorter-no-sort";
        tableRef.appendChild(tBody);
        var newRow = tBody.insertRow(-1);
        var data = info.overall.data;
        for(var index=0;index < data.length; index++){
            var cell = newRow.insertCell(-1);
            cell.innerHTML = formatter ? formatter(index, data[index]): data[index];
        }
    }

    // Create regular body
    tBody = document.createElement('tbody');
    tableRef.appendChild(tBody);

    var regexp;
    if(seriesFilter) {
        regexp = new RegExp(seriesFilter, 'i');
    }
    // Populate body with data.items array
    for(var index=0; index < info.items.length; index++){
        var item = info.items[index];
        if((!regexp || filtersOnlySampleSeries && !info.supportsControllersDiscrimination || regexp.test(item.data[seriesIndex]))
                &&
                (!showControllersOnly || !info.supportsControllersDiscrimination || item.isController)){
            if(item.data.length > 0) {
                var newRow = tBody.insertRow(-1);
                for(var col=0; col < item.data.length; col++){
                    var cell = newRow.insertCell(-1);
                    cell.innerHTML = formatter ? formatter(col, item.data[col]) : item.data[col];
                }
            }
        }
    }

    // Add support of columns sort
    table.tablesorter({sortList : defaultSorts});
}

$(document).ready(function() {

    // Customize table sorter default options
    $.extend( $.tablesorter.defaults, {
        theme: 'blue',
        cssInfoBlock: "tablesorter-no-sort",
        widthFixed: true,
        widgets: ['zebra']
    });

    var data = {"OkPercent": 69.23076923076923, "KoPercent": 30.76923076923077};
    var dataset = [
        {
            "label" : "FAIL",
            "data" : data.KoPercent,
            "color" : "#FF6347"
        },
        {
            "label" : "PASS",
            "data" : data.OkPercent,
            "color" : "#9ACD32"
        }];
    $.plot($("#flot-requests-summary"), dataset, {
        series : {
            pie : {
                show : true,
                radius : 1,
                label : {
                    show : true,
                    radius : 3 / 4,
                    formatter : function(label, series) {
                        return '<div style="font-size:8pt;text-align:center;padding:2px;color:white;">'
                            + label
                            + '<br/>'
                            + Math.round10(series.percent, -2)
                            + '%</div>';
                    },
                    background : {
                        opacity : 0.5,
                        color : '#000'
                    }
                }
            }
        },
        legend : {
            show : true
        }
    });

    // Creates APDEX table
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [0.6923076923076923, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [0.6, 500, 1500, "GET /api/communication/conversations"], "isController": false}, {"data": [0.5833333333333334, 500, 1500, "GET /api/communication/conversations/non-lus"], "isController": false}, {"data": [0.85, 500, 1500, "GET /api/communication/appels"], "isController": false}, {"data": [0.85, 500, 1500, "GET /api/communication/acheminement/unites"], "isController": false}, {"data": [0.375, 500, 1500, "GET /api/communication/conversations/{id}/acheminement"], "isController": false}, {"data": [0.4, 500, 1500, "GET /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [0.0, 500, 1500, "GET /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [0.7894736842105263, 500, 1500, "GET /api/communication/appels/en-cours"], "isController": false}, {"data": [0.7647058823529411, 500, 1500, "GET /api/communication/brouillons"], "isController": false}, {"data": [0.6666666666666666, 500, 1500, "GET /api/communication/conversations/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/auth/login"], "isController": false}]}, function(index, item){
        switch(index){
            case 0:
                item = item.toFixed(3);
                break;
            case 1:
            case 2:
                item = formatDuration(item);
                break;
        }
        return item;
    }, [[0, 0]], 3);

    // Create statistics table
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 130, 40, 30.76923076923077, 9130.684615384616, 1, 30002, 3.0, 30001.9, 30002.0, 30002.0, 1.805455252485973, 2.1365864873826452, 0.718859260423032], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["GET /api/communication/conversations", 15, 6, 40.0, 11906.933333333332, 1, 30002, 3.0, 30002.0, 30002.0, 30002.0, 0.22716258783620064, 0.4207240428882966, 0.07824982110946209], "isController": false}, {"data": ["GET /api/communication/conversations/non-lus", 12, 5, 41.666666666666664, 12464.25, 2, 30002, 5.5, 30002.0, 30002.0, 30002.0, 0.18453305448338433, 0.2956012894247182, 0.05874782789217119], "isController": false}, {"data": ["GET /api/communication/appels", 20, 3, 15.0, 4432.4, 1, 30001, 2.0, 29504.600000000002, 29978.7, 30001.0, 0.29388426837511383, 0.17340319819555058, 0.12923445903253297], "isController": false}, {"data": ["GET /api/communication/acheminement/unites", 20, 3, 15.0, 4362.4, 2, 29564, 3.0, 29014.100000000002, 29539.0, 29564.0, 0.5254446575414445, 0.8948979323752725, 0.2498940900612143], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/acheminement", 8, 5, 62.5, 18691.125, 2, 30002, 29756.0, 30002.0, 30002.0, 30002.0, 0.12696195902302773, 0.2036474782180889, 0.03291835949278697], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/brouillon", 5, 3, 60.0, 17901.4, 4, 30002, 29511.0, 30002.0, 30002.0, 30002.0, 0.0806568695455792, 0.07352062698617541, 0.03327095868755142], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/messages", 4, 4, 100.0, 30001.0, 30000, 30002, 30001.0, 30002.0, 30002.0, 30002.0, 0.12899480795897963, 0.3374776274629946, 0.0], "isController": false}, {"data": ["GET /api/communication/appels/en-cours", 19, 4, 21.05263157894737, 6244.0526315789475, 1, 30002, 3.0, 30002.0, 30002.0, 30002.0, 0.27920236293368206, 0.19764875332471235, 0.11783154821383962], "isController": false}, {"data": ["GET /api/communication/brouillons", 17, 4, 23.529411764705884, 6860.176470588236, 1, 29547, 3.0, 29139.0, 29547.0, 29547.0, 0.257404154805887, 0.12442876623917389, 0.12015545507540429], "isController": false}, {"data": ["GET /api/communication/conversations/{id}", 9, 3, 33.333333333333336, 9837.666666666666, 2, 30001, 3.0, 30001.0, 30001.0, 30001.0, 0.14281180577594413, 0.14887077019200254, 0.06421572516661377], "isController": false}, {"data": ["POST /api/auth/login", 1, 0, 0.0, 79.0, 79, 79, 79.0, 79.0, 79.0, 79.0, 12.658227848101266, 26.218848892405063, 4.1658425632911396], "isController": false}]}, function(index, item){
        switch(index){
            // Errors pct
            case 3:
                item = item.toFixed(2) + '%';
                break;
            // Mean
            case 4:
            // Mean
            case 7:
            // Median
            case 8:
            // Percentile 1
            case 9:
            // Percentile 2
            case 10:
            // Percentile 3
            case 11:
            // Throughput
            case 12:
            // Kbytes/s
            case 13:
            // Sent Kbytes/s
                item = item.toFixed(2);
                break;
        }
        return item;
    }, [[0, 0]], 0, summaryTableHeader);

    // Create error table
    createTable($("#errorsTable"), {"supportsControllersDiscrimination": false, "titles": ["Type of error", "Number of errors", "% in errors", "% in all samples"], "items": [{"data": ["The operation lasted too long: It took 29,046 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,064 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,037 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,549 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,985 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,547 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,511 milliseconds, but should not have lasted longer than 500 milliseconds.", 2, 5.0, 1.5384615384615385], "isController": false}, {"data": ["The operation lasted too long: It took 29,011 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,564 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,555 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,537 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,510 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,536 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,003 milliseconds, but should not have lasted longer than 500 milliseconds.", 2, 5.0, 1.5384615384615385], "isController": false}, {"data": ["The operation lasted too long: It took 29,051 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 28,565 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["The operation lasted too long: It took 29,040 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, 2.5, 0.7692307692307693], "isController": false}, {"data": ["Non HTTP response code: java.net.SocketTimeoutException/Non HTTP response message: Read timed out", 21, 52.5, 16.153846153846153], "isController": false}]}, function(index, item){
        switch(index){
            case 2:
            case 3:
                item = item.toFixed(2) + '%';
                break;
        }
        return item;
    }, [[1, 1]]);

        // Create top5 errors by sampler
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 130, 40, "Non HTTP response code: java.net.SocketTimeoutException/Non HTTP response message: Read timed out", 21, "The operation lasted too long: It took 29,511 milliseconds, but should not have lasted longer than 500 milliseconds.", 2, "The operation lasted too long: It took 29,003 milliseconds, but should not have lasted longer than 500 milliseconds.", 2, "The operation lasted too long: It took 29,046 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "The operation lasted too long: It took 29,064 milliseconds, but should not have lasted longer than 500 milliseconds.", 1], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": ["GET /api/communication/conversations", 15, 6, "Non HTTP response code: java.net.SocketTimeoutException/Non HTTP response message: Read timed out", 4, "The operation lasted too long: It took 29,536 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "The operation lasted too long: It took 29,040 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "", "", "", ""], "isController": false}, {"data": ["GET /api/communication/conversations/non-lus", 12, 5, "Non HTTP response code: java.net.SocketTimeoutException/Non HTTP response message: Read timed out", 4, "The operation lasted too long: It took 29,537 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "", "", "", "", "", ""], "isController": false}, {"data": ["GET /api/communication/appels", 20, 3, "The operation lasted too long: It took 29,051 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "The operation lasted too long: It took 29,555 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "Non HTTP response code: java.net.SocketTimeoutException/Non HTTP response message: Read timed out", 1, "", "", "", ""], "isController": false}, {"data": ["GET /api/communication/acheminement/unites", 20, 3, "The operation lasted too long: It took 29,064 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "The operation lasted too long: It took 28,565 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "The operation lasted too long: It took 29,564 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "", "", "", ""], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/acheminement", 8, 5, "Non HTTP response code: java.net.SocketTimeoutException/Non HTTP response message: Read timed out", 4, "The operation lasted too long: It took 29,511 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "", "", "", "", "", ""], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/brouillon", 5, 3, "The operation lasted too long: It took 29,985 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "The operation lasted too long: It took 29,511 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "Non HTTP response code: java.net.SocketTimeoutException/Non HTTP response message: Read timed out", 1, "", "", "", ""], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/messages", 4, 4, "Non HTTP response code: java.net.SocketTimeoutException/Non HTTP response message: Read timed out", 4, "", "", "", "", "", "", "", ""], "isController": false}, {"data": ["GET /api/communication/appels/en-cours", 19, 4, "Non HTTP response code: java.net.SocketTimeoutException/Non HTTP response message: Read timed out", 2, "The operation lasted too long: It took 29,046 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "The operation lasted too long: It took 29,549 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "", "", "", ""], "isController": false}, {"data": ["GET /api/communication/brouillons", 17, 4, "The operation lasted too long: It took 29,003 milliseconds, but should not have lasted longer than 500 milliseconds.", 2, "The operation lasted too long: It took 29,037 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "The operation lasted too long: It took 29,547 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "", "", "", ""], "isController": false}, {"data": ["GET /api/communication/conversations/{id}", 9, 3, "The operation lasted too long: It took 29,011 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "The operation lasted too long: It took 29,510 milliseconds, but should not have lasted longer than 500 milliseconds.", 1, "Non HTTP response code: java.net.SocketTimeoutException/Non HTTP response message: Read timed out", 1, "", "", "", ""], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
