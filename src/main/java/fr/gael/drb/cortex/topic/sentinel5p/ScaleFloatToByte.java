/*
 * Data HUb Service (DHuS) - For Space data distribution.
 * Copyright (C) 2013,2014,2015,2016 European Space Agency (ESA)
 * Copyright (C) 2013,2014,2015,2016 GAEL Systems
 * Copyright (C) 2013,2014,2015,2016 Serco Spa
 *
 * This file is part of DHuS software sources.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.gael.drb.cortex.topic.sentinel5p;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;

import javax.media.jai.RenderedImageList;

import org.apache.log4j.Logger;

import fr.gael.drb.DrbItem;
import fr.gael.drb.DrbNode;
import fr.gael.drb.DrbSequence;
import fr.gael.drb.query.Query;
import fr.gael.drb.value.Value;
import fr.gael.drbx.image.DrbImage;

/**
 * This class provides tools to manage conversion from float images data to
 * stretched byte format.
 */
public class ScaleFloatToByte 
{

   private static final Logger LOGGER=Logger.getLogger(ScaleFloatToByte.class);
   
   /**
    * Compute the input float image source to generate greyscale quicklook 
    * imagery. 
    * @param sources input float source.
    * @param nodata no data value (also called "Fill Value"
    * @param scale scale value to apply to each pixels.
    * @param offset offset to apply to each pixels.
    * @return
    */
   public static RenderedImage scaleFloatToByte (RenderedImage sources, 
      double nodata, double scale, double offset)
   {
      // Check the input values
      if (scale == 0) scale=1.0;
      return toByteImage (sources, nodata, scale, offset);
   }

   /**
    * Double value extraction facility. This method gathers all the query 
    * commands to extract float values pointed by the given XPath parameter.
    * @param sources the data source to get the node context.
    * @param xpath the xpath to extract double value.
    * @return the extracted double value.
    */
   public static Double extract (RenderedImage sources, String xpath)
   {
      Query q = new Query (xpath);
      DrbImage image = (DrbImage)((RenderedImageList)sources).get(0);
      
      DrbNode node = ((DrbNode) (image.getItemSource()));
      DrbSequence s = q.evaluate (node);
      
      if (s==null || s.getLength()==0 || s.getItem(0).getItemType()!=DrbItem.VALUE_ITEM)
      {
         LOGGER.error(String.format("Nothing extracted from %s %s",
            node.getName(), xpath));
         return 0.0;
      }
      return ((fr.gael.drb.value.Double)s.getItem(0).getValue().
         convertTo(Value.DOUBLE_ID)).doubleValue();
   }
   
   
   static BufferedImage toByteImage (RenderedImage band, double nodata, 
      double scale, double offset)
   {
      int width = band.getData().getWidth();
      int height = band.getData().getHeight();
      
      float min=Float.MAX_VALUE;
      float max=Float.MIN_VALUE;
      
      float[] out = new float[width*height];
      LOGGER.debug (String.format(
         "Converting to byte image w=%d, h=%d, nodata=%f, scale=%f, offset=%f",
         width, height, nodata, scale, offset));

      for (int i = 0; i < height; i++)
      {
         for (int j = 0; j < width; j++)
         {
            int pos=(i*width)+j;
            float pixel = band.getData().getSampleFloat(j, i, 0);
            
            if (pixel == nodata)
            {
               out[pos]=0.0f;
            }
            else
            {
               double radiance = pixel*scale+offset;
               out[pos] = (float)Math.max(1., radiance);
               // Compute the extrema
               if (out[pos]>max)
                  max=out[pos];
               if (out[pos]<min)
                  min=out[pos];
               
               LOGGER.debug(String.format("Pixel (%d,%d)=%f -> %f", j, i, pixel, radiance));
            }
            
         }
      }

      // Generate Byte image  according to the computed extrema
      float range = (float)(max-min);
      float factor = getFactor (range, 256);
      DataBuffer buffer = new DataBufferByte(width*height);
      
      for (int i = 0; i < height; i++)
      {
         for (int j = 0; j < width; j++)
         {
            int pos=(i*width)+j;
            int pixel = (int)out[pos];
            
            if (pixel!=0)
               pixel = (int)((out[pos]-min)*factor);
            
            buffer.setElem(pos, pixel);
         }
      }
      
      SampleModel sampleModel = new BandedSampleModel (
         DataBuffer.TYPE_BYTE, width, height, 1);
      
      WritableRaster raster = Raster.createWritableRaster(sampleModel, buffer, null);
      
      ColorSpace colorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
      ColorModel colorModel = new ComponentColorModel(colorSpace, false, false, 
         Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);

      BufferedImage image = new BufferedImage(colorModel, raster, 
         colorModel.isAlphaPremultiplied(), null);
      
      return image;
   }

   static float getFactor(float range, int expected_range)
   {
      // Case of no pixel (range<0) or only one pixel value (range=0): no scale.
      if (range <= 0) return 1.0f;

      return expected_range/range;
   }
}
