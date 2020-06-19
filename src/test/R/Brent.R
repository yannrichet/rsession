#help: Brent method for 1D root finding
#author: Miguel Munoz Zuniga
#ref: https://en.wikipedia.org/wiki/Brent%27s_method
#tags: Inversion
#options: ytarget='0.0';ytol='3.e-8';xtol='1.e-8';max_iterations='100'
#input: x=list(min=0,max=1)
#output: y=0.01

Brent <- function(options) {
    brent = new.env()

    brent$ytol <- as.numeric(options$ytol)
    brent$xtol <- as.numeric(options$xtol)
    brent$ytarget <- as.numeric(options$ytarget)
    brent$max_iterations <- as.integer(options$max_iterations)
    brent$i = NA

    return(brent)
}

#' first design building.
#' @param input variables description (min/max, properties, ...)
#' @param output values of interest description
getInitialDesign <- function(brent, input, output) {
    if (length(input)!=1) stop("Cannot find root of more than 1D function")
    brent$i <- 0
    brent$input <- input
    brent$exit <- -1    # Reason end of algo
    x = matrix(c(0, 1, 1),ncol=1)
    names(x) <- names(input)
    return(from01(x,brent$input))
}

## iterated design building.
## @param X data frame of current doe variables
## @param Y data frame of current results
## @return data frame or matrix of next doe step
getNextDesign <- function(brent, X, Y) {
    names(X) = names(brent$input)
    X = to01(X,brent$input)
    Y = as.matrix(Y,ncol=1) - brent$ytarget

    if (brent$i >= brent$max_iterations) {
        brent$exit <- 2
        return(NULL)
    }

    brent$i <- brent$i + 1

    a <- as.numeric(X[nrow(X) - 2, 1])
    b <- as.numeric(X[nrow(X) - 1, 1])
    c <- as.numeric(X[nrow(X), 1])
    fa <- as.numeric(Y[length(Y) - 2,1])
    fb <- as.numeric(Y[length(Y) - 1,1])
    fc <- as.numeric(Y[length(Y),1])
    if ((brent$i == 1) & (fa * fb > 0)) {
        # root must be bracketed for Brent
        brent$exit <- 1
        return(NULL)
    }

    if (fb * fc > 0) {
        #Rename a, b, c and adjust bounding interval d
        c <- a
        fc <- fa
        d <<- b - a
        e <<- d
    }
    #else { d = c-b ; e = d}
    if (abs(fc) < abs(fb)) {
        # b stand for the best approx of the root which will lie between b and c
        a = b
        b = c
        c = a
        fa = fb
        fb = fc
        fc = fa
    }

    tol1 = 2. * brent$ytol * abs(b) + 0.5 * brent$xtol # Convergence check tolerance.
    xm = .5 * (c - b)
    if ((abs(xm) <= tol1) | (fb == 0)) {
        # stop if fb = 0 return root b or tolerance reached
        brent$exit <- 0
        return(NULL)
    }
    if ((abs(e) >= tol1) & (abs(fa) > abs(fb))) {
        s = fb / fa
        if (a == c) {
            #Attempt linear interpolation
            #print("Alinear")
            p = 2. * xm * s
            q = 1. - s
        } else {
            #Attempt inverse quadratic interpolation.
            #print("Aquadratic")
            q = fa / fc
            r = fb / fc
            p = s * (2. * xm * q * (q - r) - (b - a) * (r - 1.))
            q = (q - 1.) * (r - 1.) * (s - 1.)
        }

        if (p > 0) {
            q = -q # Check whether in bounds.
        }
        p = abs(p)
        if (2. * p < min(3. * xm * q - abs(tol1 * q), abs(e * q))) {
            #print("confirmInterpol")
            e <<- d #Accept interpolation.
            d <<- p / q
        } else {
            #print("bisection1")
            d <<- xm #Interpolation failed, use bisection.
            e <<- d
        }
    } else {
        # Bounds decreasing too slowly, use bisection.
        #print("bisection2")
        d = xm
        e <<- d
    }
    a = b #Move last best guess to a.
    fa = fb
    if (abs(d) > tol1) {
        #then Evaluate new trial root.
        b = b + d
    } else {
        b = b + sign(xm) * tol1
    }
    Xnext = matrix(c(a, b, c),ncol=1)
    names(Xnext) <- names(brent$input)
    return(from01(Xnext,brent$input))
}

## final analysis. Return HTML string
## @param X data frame of doe variables
## @param Y data frame of  results
## @return HTML string of analysis
displayResults <- function(brent, X, Y) {
    if (brent$exit == 1) {
        exit.txt = "root not bracketed"
    }else if (brent$exit == 2){
        exit.txt = "maximum iteration reached"
    }else if (brent$exit == 0){
        exit.txt = "algorithm converged"
    }else{
        exit.txt = paste("error code", brent$exit)
    }
    brent$files <- paste("result", brent$i, ".png", sep = "")

    png(file = brent$files, height = 600, width = 600)
    plot(X[,1],Y[,1], pch = 20)
    #plot(as.matrix(X[3*i-1,1]),as.matrix(Y[3*i-1,1]),pch=20,col="grey70")
    abline(h = brent$ytarget,           lty = 2,           col = "grey70")
    dev.off()

    html <- paste0(' <HTML name="Root">in iteration number ',brent$i,'.<br/>',
            'the root approximation is ', X[nrow(X)-1, 1], '.<br/>',
            'corresponding to the value ', Y[nrow(X)-1, 1],'<br/>',
            '<img src="',  brent$files,  '" width="600" height="600"/>',
            '<br/>Exit due to ', exit.txt, '<br/></HTML>')

    arg <- paste0('<root>',X[nrow(X)-1, 1],'</root>')

    return(paste0(html,arg))
}

displayResultsTmp <- displayResults

from01 = function(X, inp) {
    for (i in 1:ncol(X)) {
        namei = names(X)[i]
        X[,i] = X[,i] * (inp[[ namei ]]$max-inp[[ namei ]]$min) + inp[[ namei ]]$min
    }
    return(X)
}

to01 = function(X, inp) {
    for (i in 1:ncol(X)) {
        namei = names(X)[i]
        X[,i] = (X[,i] - inp[[ namei ]]$min) / (inp[[ namei ]]$max-inp[[ namei ]]$min)
    }
    return(X)
}

##############################################################################################
# @test
# f <- function(X) matrix(Vectorize(function(x) {((x+5)/15)^3})(X),ncol=1)
# 
# options = list(ytarget=0.3,ytol=3.e-8,xtol=1.e-8,max_iterations=100)
# b = Brent(options)
# 
# X0 = getInitialDesign(b, input=list(x=list(min=-5,max=10)), NULL)
# Y0 = f(X0)
# Xi = X0
# Yi = Y0
# 
# finished = FALSE
# while (!finished) {
#     Xj = getNextDesign(b,Xi,Yi)
#     if (is.null(Xj) | length(Xj) == 0) {
#         finished = TRUE
#     } else {
#         Yj = f(Xj)
#         Xi = rbind(Xi,Xj)
#         Yi = rbind(Yi,Yj)
#     }
# }
# 
# print(displayResults(b,Xi,Yi))