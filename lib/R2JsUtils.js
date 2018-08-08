

(function () {
  'use strict';

  utils = {

	// Write string to file at path
	writeCsv: function (path, data) {
		var FileWriter=Java.type('java.io.FileWriter');
		var olinkfile = path;
		var fw = new FileWriter(olinkfile);
		fw.write(data);
		fw.close();
	},

	// Read a file and return a string
	readCsv: function (path) {
		var Paths = Java.type('java.nio.file.Paths');
		var Files = Java.type('java.nio.file.Files');
		var lines = Files.readAllLines(Paths.get(path), Java.type('java.nio.charset.StandardCharsets').UTF_8);
		var data = [];
		lines.forEach(function(line) { data.push(line); });
		return data.join('\\n');
	},

	// Remove in a string array all strings that are not matching regex expression 
	removeMatching: function (originalArray, regex) {
	    var j = 0;
	    while (j < originalArray.length) {
		if (!regex.test(originalArray[j]))
		    originalArray.splice(j, 1);
		else
		    j++;
	    }
	    return originalArray;
	},

	expendArray: function (array, length) {
		
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
	},

	/**
	* Create a range with numbers. End is included.
	* This function, instead of math.range includes end
	* @param {number} start
	* @param {number} end
	* @param {number} step
	* @returns {Array} range
	* @private
	*/
	range: function (start, end) {
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


  };

  // CommonJS module is defined
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = utils;
  }
}).call(this);

