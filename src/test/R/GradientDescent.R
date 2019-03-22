#help: First-order local optimization algorithm<br/>http://en.wikipedia.org/wiki/Gradient_descent
#tags: Optimization
#options: nmax=10; delta=0.1; epsilon=0.01; target=0.0
#input: x=list(min=0,max=1)
#output: y=0.99

#' constructor and initializer of R session
GradientDescent <- function(options) {
    gradientdescent = new.env()

    gradientdescent$nmax <- as.integer(options$nmax)
    gradientdescent$delta <- as.numeric(options$delta)
    gradientdescent$epsilon <- as.numeric(options$epsilon)
    gradientdescent$target <- as.numeric(options$target)
    gradientdescent$i = 0

    return(gradientdescent)
}

#' first design building. All variables are set in [min,max]
#' @param input variables description (min/max, properties, ...)
#' @param output values of interest description
getInitialDesign <- function(gradientdescent,input,output) {
    gradientdescent$i = 0
    gradientdescent$input <- input
    d = length(input)
    x = askfinitedifferences(rep(0.5,d),gradientdescent$epsilon)
    names(x) <- names(input)
    return(from01(x,gradientdescent$input))
}

#' iterated design building.
#' @param X data frame of current doe variables
#' @param Y data frame of current results
#' @return data frame or matrix of next doe step
getNextDesign <- function(gradientdescent,X,Y) {
    #normalize delta factor of gradient by fun range
    if (gradientdescent$i == 1) {gradientdescent$delta = gradientdescent$delta / (max(Y[,1])-min(Y[,1]))}

    if (gradientdescent$i > gradientdescent$nmax) 
        return()

    if (min(Y[,1]) < gradientdescent$target) return()

    names(X) <- names(gradientdescent$input)
    X = to01(X,gradientdescent$input)

    d = ncol(X)
    n = nrow(X)

    prevXn = X[(n-d):n,] #as.matrix(X[(n-d):n,])
    prevYn = Y[(n-d):n,1] #as.array(Y[(n-d):n,1])

    if (gradientdescent$i > 1) {
        if (Y[n-d,1] > Y[n-d-d,1]) {
            gradientdescent$delta <- gradientdescent$delta / 2
            prevXn = X[(n-d-d-1):(n-d-1),] #as.matrix(X[(n-d-d-1):(n-d-1),])
            prevYn = Y[(n-d-d-1):(n-d-1),1] #as.array(Y[(n-d-d-1):(n-d-1),1])
        }
    }
    if (d==1) prevXn = matrix(prevXn,ncol=d)

    grad = gradient(prevXn,prevYn)

    # grad = grad / sqrt(sum(grad * grad))
    xnext = t(prevXn[1,] - (grad * gradientdescent$delta))
    for (t in 1:d) {
        if (xnext[t] > 1.0) {
            xnext[t] = 1.0 ;
        }
        if (xnext[t] < 0.0) {
            xnext[t] = 0.0;
        }
    }

    gradientdescent$i <- gradientdescent$i+1

    x = askfinitedifferences(xnext,gradientdescent$epsilon)
    names(x) <- names(gradientdescent$input)

    return(from01(x,gradientdescent$input))
}

#' final analysis. Return HTML string
#' @param X data frame of doe variables
#' @param Y data frame of  results
#' @return HTML string of analysis
displayResults <- function(gradientdescent,X,Y) {
    Y = Y[,1]
    m = min(Y)
    m.ix = which.min(Y)[1]
    x = X[m.ix,] #as.matrix(X)[m.ix,]

    resolution <- 600
    d = dim(X)[2]

    if(d>1) {
        gradientdescent$files <- paste("pairs_",gradientdescent$i-1,".png",sep="")
        png(file=gradientdescent$files,bg="transparent",height=resolution,width = resolution)
        red = (as.matrix(Y)-min(Y))/(max(Y)-min(Y))
        pairs(X,col=rgb(r=red,g=0,b=1-red),Y=Y[[1]],d=d) #,panel=panel.vec)
        dev.off()
    } else {
        gradientdescent$files <- paste("plot_",gradientdescent$i-1,".png",sep="")
        png(file=gradientdescent$files,bg="transparent",height=resolution,width = resolution)
        red = (as.matrix(Y)-min(Y))/(max(Y)-min(Y))
        plot(x=X[,1],y=Y,xlab=names(X),ylab=names(Y),col=rgb(r=red,g=0,b=1-red))
        dev.off()
    }

    html=paste(sep='<br/>',
        paste0('<HTML name="minimum">minimum is ',m),
        paste0('found at ',
            paste0(paste(names(X),'=',x)),
            '<br/><img src="',
            gradientdescent$files,
            '" width="',resolution,'" height="',resolution,
            '"/></HTML>'))

    plotmin=paste('<Plot1D name="min">',m,'</Plot1D>')

    if (d == 1) {
        plotx=paste('<Plot1D name="argmin">',paste(x),'</Plot1D>')
    } else if (d == 2) {
        plotx=paste('<Plot2D name="argmin">[',paste(collapse=',',x),']</Plot2D>')
    } else {
        plotx=paste('<PlotnD name="argmin">[',paste(collapse=',',x),']</PlotnD>')
    }

    return(paste(html,plotmin,plotx,collapse=';'))
}

# panel.vec <- function(x, y , col, Y, d, ...) {
#     #points(x,y,col=col)
#     for (i in 1:(length(x)/(d+1))) {
#         n0 = 1+(i-1)*(d+1)
#         x0 = x[n0]
#         y0 = y[n0]
#         for (j in 1:d) {
#             if (x[n0+j] != x0) {
#                 dx = (Y[n0]-Y[n0+j])/(max(Y)-min(Y))
#                 #break;
#             }
#         }
#         for (j in 1:d) {
#             if (y[n0+j] != y0) {
#                 dy = (Y[n0]-Y[n0+j])/(max(Y)-min(Y))
#                 #break;
#             }
#         }
#         points(x=x0,y=y0,col=col[n0],pch=20)
#         lines(x=c(x0,x0+dx),y=c(y0,y0+dy),col=col[n0])
#         if (exists("x0p")) {
#             lines(x=c(x0p,x0),y=c(y0p,y0),col=col[n0],lty=3)
#         }
#         x0p=x0
#         y0p=y0
#     }
# 
# }

#' temporary analysis. Return HTML string
#' @param X data frame of doe variables
#' @param Y data frame of  results
#' @returnType String
#' @return HTML string of analysis
displayResultsTmp <- function(gradientdescent,X,Y) {
    displayResults(gradientdescent,X,Y)
}

###################################################################

askfinitedifferences <- function(x,epsilon) {
    xd <- matrix(x,nrow=1);
print(length(x))
    for (i in 1:length(x)) {
        xdi <- as.array(x);
        if (xdi[i] + epsilon > 1.0) {
            xdi[i] <- xdi[i] - epsilon;
        } else {
            xdi[i] <- xdi[i] + epsilon;
        }
        # xd <- rbind(xd,xdi,deparse.level = 0)
        xd <- rbind(xd,matrix(xdi,nrow=1))
    }
    return(xd)
}

gradient <- function(xd,yd) {
    d = ncol(xd)
    grad = rep(0,d)
    for (i in 1:d) {
        grad[i] = (yd[i+1] - yd[1]) / (xd[i+1,i] - xd[1,i])
    }
    return(grad)
}

from01 = function(X, inp) {
    nX = names(X)
    for (i in 1:ncol(X)) {
        namei = nX[i]
        X[,i] = X[,i] * (inp[[ namei ]]$max-inp[[ namei ]]$min) + inp[[ namei ]]$min
    }
    return(X)
}

to01 = function(X, inp) {
    nX = names(X)
    for (i in 1:ncol(X)) {
        namei = nX[i]
        X[,i] = (X[,i] - inp[[ namei ]]$min) / (inp[[ namei ]]$max-inp[[ namei ]]$min)
    }
    return(X)
}

##############################################################################################
# @test
# f <- function(X) matrix(apply(X,1,function (x) {
#     x1 <- x[1] * 15 - 5
#     x2 <- x[2] * 15
#     (x2 - 5/(4 * pi^2) * (x1^2) + 5/pi * x1 - 6)^2 + 10 * (1 - 1/(8 * pi)) * cos(x1) + 10
# }),ncol=1)
# # f1 = function(x) f(cbind(.5,x))
# 
# options = list(nmax = 10, delta = 0.1, epsilon = 0.01, target=0)
# gd = GradientDescent(options)
# 
# X0 = getInitialDesign(gd, input=list(x1=list(min=0,max=1),x2=list(min=0,max=1)), NULL)
# Y0 = f(X0)
# # X0 = getInitialDesign(gd, input=list(x2=list(min=0,max=1)), NULL)
# # Y0 = f1(X0)
# Xi = X0
# Yi = Y0
# 
# finished = FALSE
# while (!finished) {
#     Xj = getNextDesign(gd,Xi,Yi)
#     if (is.null(Xj) | length(Xj) == 0) {
#         finished = TRUE
#     } else {
# #        Yj = f1(Xj)
#         Yj = f(Xj)
#         Xi = rbind(Xi,Xj)
#         Yi = rbind(Yi,Yj)
#     }
# }
# 
# print(displayResults(gd,Xi,Yi))
