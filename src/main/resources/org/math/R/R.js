//! moment.js

;(function (global, factory) {
    typeof exports === 'object' && typeof module !== 'undefined' ? module.exports = factory() :
    typeof define === 'function' && define.amd ? define(factory) :
    global.R = factory()
}(this, (function () { 'use strict';

    function hooks () {
        return createLocal.apply(null, arguments);
    }

    function createLocal () {
        return new _R();
    }

    // R prototype object
    function _R() {
    }
    
    
    function fileExists (path) {
        var Paths = Java.type('java.nio.file.Paths');
        var Files = Java.type('java.nio.file.Files');
        return Files.exists(Paths.get(path))
    }
    
    // Write string to file at path
    function write (path, data) {
        var FileWriter=Java.type('java.io.FileWriter');
        var olinkfile = path;
        var fw = new FileWriter(olinkfile);
        fw.write(data);
        fw.close();
    }

    // Read a file and return a string
    function read (path) {
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

    /**
    * @param {Array} matrix
    * @returns {number} number of columns
    * @private
    */
    function ncol (X) {
        return math.subset(dim(X), math.index(1));
    }
    function nrow (X) {
        return math.subset(dim(X), math.index(0));
    }

    function names (X) {
        var n = X.names;
        if (n != null) {
            return n;
        } else {
            if (typeof(X)=="object") {
                var ns = [], key, i=0;
                for (key in X) {
                    if (X.hasOwnProperty(key))
                        if (key!="names" && key!="ncol" && key!="nrow")  
                            ns[i++]=key;
                }
                return ns;
            } else {
                var ns = [];
                for (var i=0; i < R.length(X); i++) {
                    ns[i] = "X"+(i+1);
                }
                return ns;
            }
        }
    }

    function colnames (X) {
        return names(X);
    }

    // to support indexing starting from 0 in js, while starting from 1 in R
    function _index (i,j) {
        if (typeof(j)=="undefined") {
            if (typeof(i)=="number" || typeof(i)=="object") {
                return math.index(math.subtract(i,1));
            } else if (typeof(i)=="string" ) {
                return math.index(i);
            }
        }

        if ((typeof(i)=="number" || typeof(i)=="object") && (typeof(j)=="number" || typeof(j)=="object")) {
            return math.index(math.subtract(i,1),math.subtract(j,1));
        } else if ((typeof(i)=="number" || typeof(i)=="object") && (typeof(j)=="string" )) {
            return math.index(math.subtract(i,1),j);
        } else if ((typeof(i)=="string") && (typeof(j)=="string")) {
            return math.index(i,j);
        } else if (typeof(i)=="string" && (typeof(j)=="object" || typeof(j)=="number")) {
            return math.index(i,math.subtract(j,1));
        }
    }


    function dim(obj) {
        if (obj == null) return 0;
        if (Array.isArray(obj)) {
            return math.size(obj);
        } else if (typeof(obj)=="object") {
            if (obj.hasOwnProperty("nrow") && obj.hasOwnProperty("ncol"))
                return [obj.nrow,obj.ncol];
            var s = 0, key;
            for (key in obj) {
                if (obj.hasOwnProperty(key))
                    if (key!="names" && key!="ncol" && key!="nrow")  s++;
            }
            return [s,1];
        } else {
            return null;
        }
    }

    function length(obj) {
            if (obj == null) return 0;
            var s = 0, key;
            for (key in obj) {
                if (obj.hasOwnProperty(key))
                    if (key!="names" && key!="ncol" && key!="nrow")  s++;
            }
            return s;
    }

    function rep(x,times) {
        var array = [];
        var i=0;
        while (i < times) {
            array.push(x);
            i++;
        }
        return array;
    }

    function which(x) {
        var array = [];
        var i=0;
        while (i < length(x)) {
            if (x[i]==true)
                array.push(i+1);
            i++;
        }
        return array;
    }

    function whichmin(x) {
        var array = [];
        var i=1;
        var m=x[0];
        while (i < length(x)) {
            if (x[i] == m) {
                array.push(i+1);
            } else if (x[i] < m) {
                array = [];
                array.push(i+1);
                m = x[i];
            }
            i++;
        }
        return array;
    }

    function whichmax(x) {
        var array = [];
        var i=1;
        var m=x[0];
        while (i < length(x)) {
            if (x[i] == m) {
                array.push(i+1);
            } else if (x[i] > m) {
                array = [];
                array.push(i+1);
                m = x[i];
            }
            i++;
        }
        return array;
    }

    function isFunction(x) {
        return typeof x === "function";
    }
    
    // Thrown Error if the String expression is false
    // The epxression need to be evaluated and the result passed in the argument 'isTrue'
    function stopIfNot(isTrue, expression) {
        if(!isTrue){
            throw new Error(expression + " is not TRUE");
        }
    }

    // No, ... needs ECS6
    //function c(...args) {
    //    var array = [];
    //    for (arg of args) {
    //        array.push(arg);
    //    }
    //    return array;
    //}

    function _print(x) {
        print(x);
        return x;
    }

    function apply(x,margin, f) {
        var y = [];
        if (margin==1) {
            for (var i=0;i<nrow(x); i++) {
                y[i] = f(math.squeeze(math.subset(x,math.index(i,range(0,ncol(x)-1)))));
            }
        } else if (margin==2) {
            for (var i=0;i<ncol(x); i++) {
                y[i] = f(math.squeeze(math.subset(x,math.index(range(0,nrow(x)-1),i))));
            }
        } else throw new Error("margin "+margin+" not supported");
        return y;
    }

    function _in(x) {
        if (Array.isArray(x)) { // I want _in to return x values (like keys if x was a map), not indices of the array...
            var y ={};
            for (var i in x) y[x[i]] = 666; // ugly :)
            return y;
        } else 
            return Object.keys(x);
    }

    function getwd() {
        var File = Java.type('java.io.File');
        return new File(".").getAbsolutePath();
    }
    
    function sleep(t) {
        var Thread = Java.type('java.lang.Thread')
        return Thread.sleep(t);
    }
    
    function isfunction(f) {
        return "function"==typeof(f);
    }
    
    function isnull(o) {
        return o==null;
    }
    
    var proto = _R.prototype;
    proto.fileExists = fileExists;
    proto.write = write;
    proto.read = read;
    proto.readJsonVariables = readJsonVariables;
    proto.createJsonString = createJsonString;
    proto.loadJson = loadJson;
    proto.removeMatching = removeMatching;
    proto.expendArray = expendArray;
    proto._index = _index;

    proto.range = range;
    proto.ncol = ncol;
    proto.nrow = nrow;
    proto.names = names;
    proto.dim = dim;
    proto.length = length;
    proto.rep = rep;
    proto.which = which;
    proto.whichmin = whichmin;
    proto.whichmax = whichmax;
    proto._print = _print;
    //proto.c = c;
    proto.apply = apply;
    proto._in = _in;
    proto.getwd = getwd;
    proto.sleep = sleep;
    proto.isfunction = isfunction;
    proto.isnull = isnull;
    proto.stopIfNot = stopIfNot;

    return hooks;

})));
