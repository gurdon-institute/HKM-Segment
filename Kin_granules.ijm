/* Maps nucleus area and granules in the specified channels. Measures granule area, circularity, signal intensity 
 * and distance from the nearest nucleus signal boundary (positive values are inside the signal area).
 * Requires HKM Segment.
 * 
 * 		by Richard Butler, Gurdon Institute Imaging Facility
 */

nucleusC = 1;
granuleC = newArray(2, 3);
nucleusSigma = 0.6; //(µm²)
granuleSigma = 0.15;
k = 1.4;
nucleusA = 15.0; //predicted nucleus area (µm²)
granuleMin = 10; //min required channel mean for granule detection
showLabels = true;
colours = newArray("", "yellow", "cyan", "magenta");
labelColours = newArray("", "", "#448888", "#884488");

function PCC(c1, c2){ //PCC of current Roi in current image, channel c1 vs c2
	getSelectionBounds(bx, by, bw, bh);
	Stack.setPosition(c1, 1, 1);
	values1 = newArray();
	for(x=bx;x<bx+bw;x++){
		for(y=by;y<by+bh;y++){
			if(selectionContains(x,y)){
				values1 = Array.concat(values1, getPixel(x,y));
			}
		}
	}
	Stack.setPosition(c2, 1, 1);	//separate loops to minimise channel switching
	values2 = newArray();
	for(x=bx;x<bx+bw;x++){
		for(y=by;y<by+bh;y++){
			if(selectionContains(x,y)){
				values2 = Array.concat(values2, getPixel(x,y));
			}
		}
	}
	
	n = values1.length;
	sum1=0;sum2=0;
	for(i=0;i<n;i++){
		sum1 += values1[i];
		sum2 += values2[i];
	}
	mean1 = sum1/n;
	mean2 = sum2/n;
	cov=0;sd1=0;sd2=0;
	for(i=0;i<n;i++){
		cov += (values1[i]-mean1) * (values2[i]-mean2);
		sd1 += (values1[i]-mean1) * (values1[i]-mean1);
		sd2 += (values2[i]-mean2) * (values2[i]-mean2);
	}
	r = cov / (sqrt(sd1)*sqrt(sd2));
	return r;
}

function scatterPlotFromTable(name){	//first two channels in granuleC for current image only
	if(granuleC.length<2){ 
		return;
	}
	A = newArray();
	B = newArray();
	for(r=0;r<nResults();r++){
		if( getResultString("Image", r)==name ){
			va = parseInt( getResult("C"+granuleC[0]+" Mean", r ) );
			vb = parseInt( getResult("C"+granuleC[1]+" Mean", r ) );
			A = Array.concat(A, va);
			B = Array.concat(B, vb);
		}
	}
	Array.getStatistics(A, minA, maxA, meanA, sdA);
	Array.getStatistics(B, minB, maxB, meanB, sdB);
	rnd = 1000;
	maxA = (floor(maxA/rnd)*rnd)+rnd;
	maxB = (floor(maxB/rnd)*rnd)+rnd;
	max = maxOf(maxA, maxB);
	Plot.create("C"+granuleC[0]+" vs C"+granuleC[1], "C"+granuleC[0], "C"+granuleC[1]);
	Plot.setLimits(0, max, 0, max);
	Plot.setFrameSize(800, 800);
	Plot.add("x",A,B);
	Plot.show();
}

function makeHistogram(values, xLabel, colour){
	if(values.length==0){
		print("no values for "+xLabel+" histogram");
		return;
	}
	for(v=0;v<values.length;v++){
		values[v] = parseFloat(values[v]);
	}
	Array.sort(values);
	min = values[0];
	max = values[values.length-1];
	range = max-min;
	n = round(sqrt(values.length));
	binW = range/n;
	hist = newArray();
	xAxis = newArray();
	for(h=0;h<n;h++){
		hist = Array.concat(hist, 0);
		xAxis = Array.concat(xAxis, min+(binW*h)+(binW));
	}
	index = 0;
	maxCount = 0;
	for(i=0;i<values.length;i++){
		index = floor( ((values[i]-min)/range)*(n-1) );
		if(index>=n){
			print(index);
			index=n-1;
		}
		hist[index]++;
		maxCount = maxOf(maxCount, hist[index]);
	}
	Plot.create(xLabel+" Histogram", xLabel, "");
	Plot.setLimits(min, max, 0, (floor(maxCount/10)*10)+10);
	W = 600;
	H = 400;
	Plot.setFrameSize(W, H);
	Plot.setLineWidth(1);	//fill with width 1 lines to avoid BasicStroke.CAP_ROUND
	Plot.setColor(colour);
	inc = range/(1.1*W);
	for(i=0;i<n;i++){
		for(w=-binW;w<=0;w+=inc){
			Plot.drawLine(xAxis[i]+w, 0, xAxis[i]+w, hist[i]);
		}
	}
	Plot.show();
}


setBatchMode(true);
roiManager("reset");
title = getTitle();
run("Select None");
run("Remove Overlay");
getDimensions(W,H,C,Z,T);
if(C<granuleC[granuleC.length-1]||Z>1||T>1){
	exit("This macro requires a 2D image with multiple channels.\n"+title+" has C"+C+" Z"+Z+" T"+T);
}
getPixelSize(unit, pixW, pixH);

run("Duplicate...", "title=nuclei duplicate range="+nucleusC+"-"+nucleusC+" use");
run("Gaussian Blur...", "sigma="+nucleusSigma+" scaled");
run("Convert to Mask", "method=RenyiEntropy background=Dark");
run("Open");
run("Exact Signed Euclidean Distance Transform (3D)");

for(c=0;c<granuleC.length;c++){
	roiManager("reset");
	selectImage(title);
	run("Select None");
	run("Duplicate...", "title=granules duplicate range="+granuleC[c]+"-"+granuleC[c]+" use");
	run("Duplicate...", "title=sub duplicate");
	selectImage("granules");
	run("Gaussian Blur...", "sigma="+granuleSigma+" scaled");
	selectImage("sub");
	run("Gaussian Blur...", "sigma="+(granuleSigma*k)+" scaled");
	imageCalculator("Subtract stack", "granules","sub");
	selectImage("sub");	close();
	selectImage("granules");
	getStatistics(area,mean);
	if(mean<=granuleMin){
		print("skipping C"+granuleC[c]+" due to lack of signal");
	}
	else{
		run("HKM Segment", "startK=16 blur=0.0 minR=0.1 maxR=0.6 threshold=Otsu watershed=false");

		distAr = newArray();
		areaAr = newArray();
		meanAr = newArray();
		circAr = newArray();
		for(r=0;r<roiManager("count");r++){
			selectImage("EDT");
			roiManager("select", r);
			getStatistics(area, meanDist);
			meanDist *= pixW;
			selectImage(title);
			roiManager("select", r);
			Stack.setPosition(granuleC[c],1,1);
			List.setMeasurements();
			x = List.getValue("X");
			y = List.getValue("Y");
			Overlay.addSelection(colours[granuleC[c]]);
			Overlay.setPosition(0,1,1);
			if(showLabels){
				setFont("SansSerif", 8);
				setColor(labelColours[granuleC[c]]);
				Overlay.drawString(""+(r+1), (x/pixW)-(lengthOf(""+(r+1))*2), (y/pixH)+2);
				Overlay.setPosition(0,1,1);
			}
			row = nResults();
			distAr = Array.concat(distAr, meanDist);
			areaAr = Array.concat(areaAr, List.getValue("Area"));
			meanAr = Array.concat(meanAr, List.getValue("Mean"));
			circAr = Array.concat(circAr, List.getValue("Circ."));
			setResult("Image", row, title);
			setResult("Map Channel", row, granuleC[c]);
			setResult("Granule", row, r+1);
			setResult("X", row, d2s(x,3) );
			setResult("Y", row, d2s(y,3) );
			setResult("Nucleus Distance ("+unit+")", row, d2s(meanDist,3) );
			setResult("Area ("+unit+fromCharCode(0178)+")", row, d2s(List.getValue("Area"),3) );
			setResult("Circularity", row, d2s(List.getValue("Circ."),3) );
			for(mc=0;mc<granuleC.length;mc++){
				Stack.setPosition(granuleC[mc],1,1);
				getStatistics(a,mean);
				setResult("C"+granuleC[mc]+" Mean", row, d2s(mean, 3) );
			}
			selectImage(title);
			roiManager("select", r);
			for(c2=0;c2<granuleC.length;c2++){
				if(granuleC[c2]!=granuleC[c]){
					pcc = PCC(granuleC[c], granuleC[c2]);
					setResult("PCC C"+granuleC[c]+" vs C"+granuleC[c2], row, pcc);
				}
			}
		}

		scatterPlotFromTable(title);
	
		makeHistogram(distAr, "Nucleus Distance ("+unit+")", "blue");
		makeHistogram(areaAr, "Area ("+unit+fromCharCode(0178)+")", "green");
		makeHistogram(circAr, "Circularity", "magenta");
		makeHistogram(meanAr, "Mean", "red");
		run("Images to Stack", "name=["+title+"-C"+granuleC[c]+"] title=Histogram use");
	}
	selectImage("granules");	close();
}

selectImage("nuclei");
run("Create Selection");
getStatistics(area);
estN = area/nucleusA;
print(title+" estimated nucleus count = "+estN);
selectImage(title);
run("Restore Selection");
Overlay.addSelection(colours[nucleusC]);
Overlay.setPosition(0,1,1);
run("Select None");

//clean up
roiManager("reset");
selectImage("nuclei"); close();
selectImage("EDT");	close();
setBatchMode("exit and display");