/*
Copyright 2011, 2012 Frederic Langlet
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
you may obtain a copy of the License at

                http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package kanzi.util;

import java.util.Collection;
import java.util.TreeSet;


public class QuadTreeGenerator
{
    private final int width;
    private final int height;
    private final int stride;
    private final int offset;
    private final int minNodeDim;
    private final boolean isRGB;

    
    public QuadTreeGenerator(int width, int height)
    {
       this(width, height, 0, width, 8);
    }

   
    public QuadTreeGenerator(int width, int height, int minNodeDim)
    {
       this(width, height, 0, width, minNodeDim);
    }

    
    public QuadTreeGenerator(int width, int height, int offset, int stride, int minNodeDim)
    {
       this(width, height, offset, stride, minNodeDim, true);
    }


    public QuadTreeGenerator(int width, int height, int offset, int stride,
            int minNodeDim, boolean isRGB)
    {
      if (height < 8)
         throw new IllegalArgumentException("The height must be at least 8");

      if (width < 8)
         throw new IllegalArgumentException("The width must be at least 8");

      if ((height & 1) != 0)
         throw new IllegalArgumentException("The height must be a multiple of 2");

      if ((width & 1) != 0)
         throw new IllegalArgumentException("The width must be a multiple of 2");

      this.width = width;
      this.height = height;
      this.stride = stride;
      this.offset = offset;
      this.minNodeDim = minNodeDim;
      this.isRGB = isRGB;
   }   

    
   // Quad-tree decomposition of the input image based on variance of each node
   // The decomposition stops when enough nodes have been computed or the minimum
   // node dimension has been reached.
   // Input nodes are reused and new nodes are added to the input collection (if needed).
   public Collection<Node> decomposeNodes(Collection<Node> list, int[] input, int nbNodes)
   {
      if (nbNodes < 4)
         throw new IllegalArgumentException("The target number of nodes must be at least 4");

      return this.decompose(list, input, nbNodes, -1);
   }


   // Quad-tree decomposition of the input image based on variance of each node
   // The decomposition stops when all the nodes in the tree has a variance lower
   // than or equak to the target variance or the minimum node dimension has been
   // reached.
   // Input nodes are reused and new nodes are added to the input collection (if needed).
   public Collection<Node> decomposeVariance(Collection<Node> list, int[] input, int variance)
   {
      if (variance < 0)
         throw new IllegalArgumentException("The target variance of nodes must be at least 0");

      return this.decompose(list, input, -1, variance);
   }


   protected Collection<Node> decompose(Collection<Node> list, int[] input,
           int nbNodes, int variance)
   {
      final TreeSet<Node> processed = new TreeSet<Node>();
      final TreeSet<Node> nodes = new TreeSet<Node>();
      final int st = this.stride;

      for (Node node : list)
      {
         if ((node.w <= this.minNodeDim) || (node.h <= this.minNodeDim))
            processed.add(node);
         else
            nodes.add(node);
      }

      if (nodes.isEmpty() == true)
      {
         final int w = this.width;
         final int h = this.height;
         final int y0 = this.offset / this.stride;
         final int x0 = this.offset - y0*this.stride;
         
         // First level
         Node root1 = Node.getNode(null, x0, y0, w>>1, h>>1);
         Node root2 = Node.getNode(null, x0+(w>>1), y0, w>>1, h>>1);
         Node root3 = Node.getNode(null, x0, y0+(h>>1), w>>1, h>>1);
         Node root4 = Node.getNode(null, x0+(w>>1), y0+(h>>1), w>>1, h>>1);

         if (this.isRGB == true)
         {
            root1.computeVarianceRGB(input, st);
            root2.computeVarianceRGB(input, st);
            root3.computeVarianceRGB(input, st);
            root4.computeVarianceRGB(input, st);
         }
         else
         {
            root1.computeVarianceY(input, st);
            root2.computeVarianceY(input, st);
            root3.computeVarianceY(input, st);
            root4.computeVarianceY(input, st);
         }

         // Add to set of nodes sorted by decreasing variance
         nodes.add(root1);
         nodes.add(root2);
         nodes.add(root3);
         nodes.add(root4);
      }
    
      while ((nodes.size() > 0) && ((nbNodes < 0) || (processed.size() + nodes.size() < nbNodes)))
      {
         Node parent = nodes.pollFirst();

         if ((parent.w <= this.minNodeDim) || (parent.h <= this.minNodeDim))
         {
            processed.add(parent);
            continue;
         }
         
         if ((variance >= 0) && (parent.variance <= variance))
         {
            processed.add(parent);
            continue;
         }

         // Create 4 children, taking into account odd dimensions
         final int pw = parent.w + 1;
         final int ph = parent.h + 1;
         final int cw = pw >> 1;
         final int ch = ph >> 1;
         Node node1 = Node.getNode(parent, parent.x, parent.y, cw, ch);
         Node node2 = Node.getNode(parent, parent.x+parent.w-cw, parent.y, cw, ch);
         Node node3 = Node.getNode(parent, parent.x, parent.y+parent.h-ch, cw, ch);
         Node node4 = Node.getNode(parent, parent.x+parent.w-cw, parent.y+parent.h-ch, cw, ch);

         if (this.isRGB == true)
         {
            node1.computeVarianceRGB(input, st);
            node2.computeVarianceRGB(input, st);
            node3.computeVarianceRGB(input, st);
            node4.computeVarianceRGB(input, st);
         }
         else
         {
            node1.computeVarianceY(input, st);
            node2.computeVarianceY(input, st);
            node3.computeVarianceY(input, st);
            node4.computeVarianceY(input, st);
         }

         // Add to set of nodes sorted by decreasing variance
         nodes.add(node1);
         nodes.add(node2);
         nodes.add(node3);
         nodes.add(node4);
      }

      nodes.addAll(processed);
      list.addAll(nodes);
      return list;
   }

   
   public static class Node implements Comparable<Node>
   {
      final Node parent;
      public final int x;
      public final int y;
      public final int w;
      public final int h;
      public int variance;


      public static Node getNode(Node parent, int x, int y, int w, int h)
      {
         return new Node(parent, x, y, w, h);
      }


      private Node(Node parent, int x, int y, int w, int h)
      {
         this.parent = parent;
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
      }

      
      @Override
      public int compareTo(Node o)
      {
         // compare by decreasing variance
         final int val = o.variance - this.variance; 
         
         if (val != 0)
            return val;
         
         // In case of equal variance values, order does not matter
         return o.hashCode() - this.hashCode();
      }
      
      
      @Override
      public boolean equals(Object o)
      {
         try
         {
           if (o == this)
              return true;
           
           Node n = (Node) o;           
           
           if (this.x != n.x)
              return false;
           
           if (this.y != n.y)
              return false;
           
           if (this.w != n.w)
              return false;
           
           if (this.h != n.h)
              return false;
           
           return true;
         }
         catch (NullPointerException e)
         {
            return false;
         }
         catch (ClassCastException e)
         {
            return false;
         }
      }


      @Override
      public int hashCode()
      {
         int hash = 3;
         hash = 79 * hash + this.x;
         hash = 79 * hash + this.y;
         hash = 79 * hash + this.w;
         hash = 79 * hash + this.h;
         //hash = 79 * hash + this.variance;
         return hash;
      }
      
      
      int computeVarianceRGB(int[] rgb, int stride)
      {
         final int iend = this.x + this.w;
         final int jend = this.y + this.h;
         final int len = this.w * this.h;
         long sq_sumR = 0, sq_sumB = 0, sq_sumG = 0;
         long sumR = 0, sumG = 0, sumB = 0;
         int offs = this.y * stride;

         for (int j=this.y; j<jend; j++)
         {
            for (int i=this.x; i<iend; i++)
            {
               final int pixel = rgb[offs+i];
               final int r = (pixel >> 16) & 0xFF;
               final int g = (pixel >>  8) & 0xFF;
               final int b =  pixel & 0xFF;
               sumR += r;
               sumG += g;
               sumB += b;
               sq_sumR += (r*r);
               sq_sumG += (g*g);
               sq_sumB += (b*b);
            }
            
            offs += stride;
         }

         final long vR = (sq_sumR - ((sumR * sumR) / len));
         final long vG = (sq_sumG - ((sumG * sumG) / len));
         final long vB = (sq_sumB - ((sumB * sumB) / len));
         this.variance = (int) ((vR + vG + vB) / (3 * len));
         return this.variance;
      }
            

      int computeVarianceY(int[] yBuffer, int stride)
      {
         final int iend = this.x + this.w;
         final int jend = this.y + this.h;
         final int len = this.w * this.h;
         long sq_sum = 0;
         long sum = 0;
         int offs = this.y * stride;

         for (int j=this.y; j<jend; j++)
         {
            for (int i=this.x; i<iend; i++)
            {
               final int pixel = yBuffer[offs+i];
               sum += pixel;
               sq_sum += (pixel*pixel);
            }

            offs += stride;
         }

         this.variance = (int) ((sq_sum - ((sum * sum) / len)) / len);
         return this.variance;
      }


      @Override
      public String toString()
      {
         StringBuilder builder = new StringBuilder(200);
         builder.append('[');
         builder.append("x=");
         builder.append(this.x);
         builder.append(", y=");
         builder.append(this.y);
         builder.append(", w=");
         builder.append(this.w);
         builder.append(", h=");
         builder.append(this.h);
         builder.append(", variance=");
         builder.append(this.variance);
         builder.append(']');
         return builder.toString();
      }
   }   
}
