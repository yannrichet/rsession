//! moment.js

;(function (global, factory) {
    typeof exports === 'object' && typeof module !== 'undefined' ? module.exports = factory() :
    typeof define === 'function' && define.amd ? define(factory) :
    global.utils = factory()
}(this, (function () { 'use strict';

    function hooks () {
        return createLocal.apply(null, arguments);
    }

    function createLocal () {
        return new Utils();
    }

    // Utils prototype object
    function Utils() {
    }
    
    
    function fileExists (path) {
        var Paths = Java.type('java.nio.file.Paths');
        var Files = Java.type('java.nio.file.Files');
        return Files.exists(Paths.get(path))
    }
    
    // Write string to file at path
    function writeCsv (path, data) {
        var FileWriter=Java.type('java.io.FileWriter');
        var olinkfile = path;
        var fw = new FileWriter(olinkfile);
        fw.write(data);
        fw.close();
    }

    // Read a file and return a string
    function readCsv (path) {
        var Paths = Java.type('java.nio.file.Paths');
        var Files = Java.type('java.nio.file.Files');
        var lines = Files.readAllLines(Paths.get(path), Java.type('java.nio.charset.StandardCharsets').UTF_8);
        var data = [];
        lines.forEach(function(line) { data.push(line); });
        return data.join('\\n');
    }
        
    // Read variables from file and return a list of all variables of the json
    function readJsonVariables (path) {
        var Paths = Java.type('java.nio.file.Paths');
        var Files = Java.type('java.nio.file.Files');
        var linesList = Files.readAllLines(Paths.get(path), Java.type('java.nio.charset.StandardCharsets').UTF_8);
        var lines = "";
        linesList.forEach(function(line) { 
            lines += line;
        });
        eval(print(lines));
        var firstLine = lines;
        var jsonObject = JSON.parse(firstLine);
        var variables = Object.keys(jsonObject);
        return variables;
    }
        
    // Create a json string containing the variable and its value
    function createJsonString (variables, jsVariableStorageObject) {
        var newObject = {};
        if(Array.isArray(variables)) {
            variables.forEach(function(variable) { 
                eval("newObject." + variable + "="+jsVariableStorageObject + "." + variable);
            });
        }else {
            eval("newObject." + variables + "="+jsVariableStorageObject + "." + variables);
        }

        var jsonString = JSON.stringify(newObject);
        return jsonString;
    }
        
    // Load variables from file at json format
    function loadJson (path, jsVariableStorageObject) {
        var Paths = Java.type('java.nio.file.Paths');
        var Files = Java.type('java.nio.file.Files');
        var linesList = Files.readAllLines(Paths.get(path), Java.type('java.nio.charset.StandardCharsets').UTF_8);
        var lines = "";
        linesList.forEach(function(line) { 
            lines += line;
        });
        eval(print(lines));
        var firstLine = lines;
        var jsonObject = JSON.parse(firstLine);
        var varList = Object.keys(jsonObject);
        varList.forEach(function(variable) { 
            var value = eval("jsonObject." + variable);
            eval(print(variable + '=' + value));
            eval(jsVariableStorageObject + "." + variable + "='" + value + "'");
        });
        return true;
    }

    // Remove in a string array all strings that are not matching regex expression 
    function removeMatching (originalArray, regex) {
        var j = 0;
        while (j < originalArray.length) {
            if (!regex.test(originalArray[j]))
                originalArray.splice(j, 1);
            else
                j++;
        }
        return originalArray;
    }

    function expendArray (array, length) {

            var expendedArray = [];

            // If 'array' is only a number
            if(!Array.isArray(array)) {

                    while(length--) expendedArray[length] = array;
                    return expendedArray;
            } else {

                    var initialArrayLength = array.length;
                    var initialArrayIndex = 0;
                    for (var i = 0; i < length; i++) {

                            expendedArray[i] = array[initialArrayIndex];

                            if (initialArrayIndex < initialArrayLength - 1) {
                                    initialArrayIndex++;
                            } else {
                                    initialArrayIndex = 0;
                            }
                    }

                    return expendedArray;
            }
    }
    
    /**
    * Create a range with numbers. End is included.
    * This function, instead of math.range includes end
    * @param {number} start
    * @param {number} end
    * @param {number} step
    * @returns {Array} range
    * @private
    */
    function range (start, end) {
        var array = [];
        var x = start;

        var step = 1;

        if(end < start) {
                step = -1;
        }

        if (step > 0) {
                while (x <= end) {
                        array.push(x);
                        x += step;
                }
        } else if (step < 0) {
                while (x >= end) {
                        array.push(x);
                        x += step;
                }
        }

        return array;
    }

    var proto = Utils.prototype;
    proto.fileExists = fileExists;
    proto.writeCsv = writeCsv;
    proto.readCsv = readCsv;
    proto.readJsonVariables = readJsonVariables;
    proto.createJsonString = createJsonString;
    proto.loadJson = loadJson;
    proto.removeMatching = removeMatching;
    proto.expendArray = expendArray;
    proto.range = range;

    return hooks;

})));
