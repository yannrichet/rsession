//! moment.js

;(function (global, factory) {
    typeof exports === 'object' && typeof module !== 'undefined' ? module.exports = factory() :
    typeof define === 'function' && define.amd ? define(factory) :
    global.rand = factory()
}(this, (function () { 'use strict';

    function hooks () {
        return createLocal.apply(null, arguments);
    }

    function createLocal () {
        return new _rand();
    }

    // rand prototype object
    function _rand() {
    }
    

// Code from Rob Britton available on github (0.1.0)
// 
// Generate uniformly distributed random numbers
// Gives a random number on the interal [min, max).
// If discrete is true, the number will be an integer.
function r1unif(min, max, discrete) {
  if (min === undefined) {
    min = 0;
  }
  if (max === undefined) {
    max = 1;
  }
  if (discrete === undefined) {
    discrete = false;
  }
  if (discrete) {
    return Math.floor(r1unif(min, max, false));
  }
  return Math.random() * (max - min) + min;
}

// Generate normally-distributed random nubmers
// Algorithm adapted from:
// http://c-faq.com/lib/gaussian.html
function r1norm(mean, stdev) {
  var u1, u2, v1, v2, s;
  if (mean === undefined) {
    mean = 0.0;
  }
  if (stdev === undefined) {
    stdev = 1.0;
  }
  if (r1norm.v2 === null) {
    do {
      u1 = Math.random();
      u2 = Math.random();

      v1 = 2 * u1 - 1;
      v2 = 2 * u2 - 1;
      s = v1 * v1 + v2 * v2;
    } while (s === 0 || s >= 1);

    r1norm.v2 = v2 * Math.sqrt(-2 * Math.log(s) / s);
    return stdev * v1 * Math.sqrt(-2 * Math.log(s) / s) + mean;
  }

  v2 = r1norm.v2;
  r1norm.v2 = null;
  return stdev * v2 + mean;
}

r1norm.v2 = null;

// Generate Chi-square distributed random numbers
function r1chisq(degreesOfFreedom) {
  if (degreesOfFreedom === undefined) {
    degreesOfFreedom = 1;
  }
  var i, z, sum = 0.0;
  for (i = 0; i < degreesOfFreedom; i++) {
    z = r1norm();
    sum += z * z;
  }

  return sum;
}

// Generate Poisson distributed random numbers
function r1poisson(lambda) {
  if (lambda === undefined) {
    lambda = 1;
  }
  var l = Math.exp(-lambda),
    k = 0,
    p = 1.0;
  do {
    k++;
    p *= Math.random();
  } while (p > l);

  return k - 1;
}

// Generate Cauchy distributed random numbers
function r1cauchy(loc, scale) {
  if (loc === undefined) {
    loc = 0.0;
  }
  if (scale === undefined) {
    scale = 1.0;
  }
  var n2, n1 = r1norm();
  do {
    n2 = r1norm();
  } while (n2 === 0.0);

  return loc + scale * n1 / n2;
}

// Bernoulli distribution: gives 1 with probability p
function r1bernoulli(p) {
  return Math.random() < p ? 1 : 0;
}

// Vectorize a random generator
function vectorize(generator) {
  return function () {
    var n, result, i, args;
    args = [].slice.call(arguments)
    n = args.shift();
    result = [];
    for (i = 0; i < n; i++) {
      result.push(generator.apply(this, args));
    }
    return result;
  };
}

// Generate a histogram from a list of numbers
//function histogram(data, binCount) {
//  binCount = binCount || 10;
//
//  var bins, i, scaled,
//    max = Math.max.apply(this, data),
//    min = Math.min.apply(this, data);
//
//  // edge case: max == min
//  if (max === min) {
//    return [data.length];
//  }
//
//  bins = [];
//
//  // zero each bin
//  for (i = 0; i < binCount; i++) {
//    bins.push(0);
//  }
//
//  for (i = 0; i < data.length; i++) {
//    // scale it to be between 0 and 1
//    scaled = (data[i] - min) / (max - min);
//
//    // scale it up to the histogram size
//    scaled *= binCount;
//
//    // drop it in a bin
//    scaled = Math.floor(scaled);
//
//    // edge case: the max
//    if (scaled === binCount) { scaled--; }
//
//    bins[scaled]++;
//  }
//
//  return bins;
//}

/**
 * Get a random element from a list
 */
//function r1list(list) {
//  return list[r1unif(0, list.length, true)];
//}

//exports.r1unif = r1unif;
//exports.r1norm = r1norm;
//exports.r1chisq = r1chisq;
//exports.r1poisson = r1poisson;
//exports.r1cauchy = r1cauchy;
//exports.r1bernoulli = r1bernoulli;
//exports.r1list = r1list;

//exports.runif = vectorize(r1unif);
//exports.rnorm = vectorize(r1norm);
//exports.rchisq = vectorize(r1chisq);
//exports.rpoisson = vectorize(r1poisson);
//exports.rcauchy = vectorize(r1cauchy);
//exports.rbernoulli = vectorize(r1bernoulli);
//exports.rlist = vectorize(r1list);

//exports.histogram = histogram;

    var proto = _rand.prototype;

proto.runif = vectorize(r1unif);
proto.rnorm = vectorize(r1norm);
proto.rchisq = vectorize(r1chisq);
proto.rpois = vectorize(r1poisson);
proto.rcauchy = vectorize(r1cauchy);
//proto.rbernoulli = vectorize(r1bernoulli);

    return hooks;

})));
