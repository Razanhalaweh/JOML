/*
 * (C) Copyright 2016 JOML

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.

 */
package org.joml.sampling;

import java.util.ArrayList;

import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Creates samples using the "Best Candidate" algorithm.
 * 
 * @author Kai Burjack
 */
public class BestCandidateSampling {

    /**
     * Generates Best Candidate samples on a unit sphere.
     * <p>
     * References:
     * <ul>
     * <li><a href="https://www.microsoft.com/en-us/research/wp-content/uploads/2005/09/tr-2005-123.pdf">Indexing the Sphere with the Hierarchical Triangular Mesh</a>
     * <li><a href="http://math.stackexchange.com/questions/1244512/point-in-a-spherical-triangle-test">Point in a spherical triangle test</a>
     * </ul>
     * 
     * @author Kai Burjack
     */
    public static class Sphere {
        private static final class OctahedronTree {
            private static final int MAX_OBJECTS_PER_NODE = 32;

            private float v0x, v0y, v0z;
            private float v1x, v1y, v1z;
            private float v2x, v2y, v2z;
            private float cx, cy, cz;
            private float arc;

            private ArrayList objects;
            private OctahedronTree[] children;

            OctahedronTree() {
                this.children = new OctahedronTree[8];
                float s = 1f;
                this.arc = (float) Math.PI;
                this.children[0] = new OctahedronTree(-s, 0, 0, 0, 0, s, 0, s, 0);
                this.children[1] = new OctahedronTree(0, 0, s, s, 0, 0, 0, s, 0);
                this.children[2] = new OctahedronTree(s, 0, 0, 0, 0, -s, 0, s, 0);
                this.children[3] = new OctahedronTree(0, 0, -s, -s, 0, 0, 0, s, 0);
                this.children[4] = new OctahedronTree(-s, 0, 0, 0, -s, 0, 0, 0, s);
                this.children[5] = new OctahedronTree(0, 0, s, 0, -s, 0, s, 0, 0);
                this.children[6] = new OctahedronTree(s, 0, 0, 0, -s, 0, 0, 0, -s);
                this.children[7] = new OctahedronTree(0, 0, -s, 0, -s, 0, -s, 0, 0);
            }

            private OctahedronTree(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2) {
                super();
                this.v0x = x0;
                this.v0y = y0;
                this.v0z = z0;
                this.v1x = x1;
                this.v1y = y1;
                this.v1z = z1;
                this.v2x = x2;
                this.v2y = y2;
                this.v2z = z2;
                cx = (v0x + v1x + v2x) / 3.0f;
                cy = (v0y + v1y + v2y) / 3.0f;
                cz = (v0z + v1z + v2z) / 3.0f;
                float invCLen = 1.0f / (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
                cx *= invCLen;
                cy *= invCLen;
                cz *= invCLen;
                float arc1 = greatCircleDist(v0x, v0y, v0z, v1x, v1y, v1z);
                float arc2 = greatCircleDist(v0x, v0y, v0z, v2x, v2y, v2z);
                float arc3 = greatCircleDist(v1x, v1y, v1z, v2x, v2y, v2z);
                float dist = Math.max(Math.max(arc1, arc2), arc3) * 2.0f;
                this.arc = dist;
            }

            private void split() {
                float w0x = v1x + v2x;
                float w0y = v1y + v2y;
                float w0z = v1z + v2z;
                float len = 1.0f / (float) Math.sqrt(w0x * w0x + w0y * w0y + w0z * w0z);
                w0x *= len;
                w0y *= len;
                w0z *= len;
                float w1x = v0x + v2x;
                float w1y = v0y + v2y;
                float w1z = v0z + v2z;
                w1x *= len;
                w1y *= len;
                w1z *= len;
                float w2x = v0x + v1x;
                float w2y = v0y + v1y;
                float w2z = v0z + v1z;
                w2x *= len;
                w2y *= len;
                w2z *= len;
                children = new OctahedronTree[4];
                /*
                 * See: https://www.microsoft.com/en-us/research/wp-content/uploads/2005/09/tr-2005-123.pdf
                 */
                children[0] = new OctahedronTree(v0x, v0y, v0z, w2x, w2y, w2z, w1x, w1y, w1z);
                children[1] = new OctahedronTree(v1x, v1y, v1z, w0x, w0y, w0z, w2x, w2y, w2z);
                children[2] = new OctahedronTree(v2x, v2y, v2z, w1x, w1y, w1z, w0x, w0y, w0z);
                children[3] = new OctahedronTree(w0x, w0y, w0z, w1x, w1y, w1z, w2x, w2y, w2z);
            }

            private void insertIntoChild(Vector3f o) {
                for (int i = 0; i < children.length; i++) {
                    OctahedronTree c = children[i];
                    /*
                     * Idea: Test whether ray from origin towards point cuts triangle
                     * 
                     * See: http://math.stackexchange.com/questions/1244512/point-in-a-spherical-triangle-test
                     */
                    if (isPointOnSphericalTriangle(o.x, o.y, o.z, c.v0x, c.v0y, c.v0z, c.v1x, c.v1y, c.v1z, c.v2x, c.v2y, c.v2z, 1E-6f)) {
                        c.insert(o);
                        return;
                    }
                }
            }

            void insert(Vector3f object) {
                if (children != null) {
                    insertIntoChild(object);
                    return;
                }
                if (objects != null && objects.size() == MAX_OBJECTS_PER_NODE) {
                    split();
                    for (int i = 0; i < objects.size(); i++) {
                        insertIntoChild((Vector3f) objects.get(i));
                    }
                    objects = null;
                    insertIntoChild(object);
                } else {
                    if (objects == null)
                        objects = new ArrayList(MAX_OBJECTS_PER_NODE);
                    objects.add(object);
                }
            }

            /**
             * This is essentially a ray cast from the origin of the sphere to the point to test and then checking whether that ray goes through the triangle.
             */
            private static boolean isPointOnSphericalTriangle(float x, float y, float z, float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y,
                    float v2Z, float epsilon) {
                float edge1X = v1X - v0X;
                float edge1Y = v1Y - v0Y;
                float edge1Z = v1Z - v0Z;
                float edge2X = v2X - v0X;
                float edge2Y = v2Y - v0Y;
                float edge2Z = v2Z - v0Z;
                float pvecX = y * edge2Z - z * edge2Y;
                float pvecY = z * edge2X - x * edge2Z;
                float pvecZ = x * edge2Y - y * edge2X;
                float det = edge1X * pvecX + edge1Y * pvecY + edge1Z * pvecZ;
                if (det > -epsilon && det < epsilon)
                    return false;
                float tvecX = -v0X;
                float tvecY = -v0Y;
                float tvecZ = -v0Z;
                float invDet = 1.0f / det;
                float u = (tvecX * pvecX + tvecY * pvecY + tvecZ * pvecZ) * invDet;
                if (u < 0.0f || u > 1.0f)
                    return false;
                float qvecX = tvecY * edge1Z - tvecZ * edge1Y;
                float qvecY = tvecZ * edge1X - tvecX * edge1Z;
                float qvecZ = tvecX * edge1Y - tvecY * edge1X;
                float v = (x * qvecX + y * qvecY + z * qvecZ) * invDet;
                if (v < 0.0f || u + v > 1.0f)
                    return false;
                float t = (edge2X * qvecX + edge2Y * qvecY + edge2Z * qvecZ) * invDet;
                return t >= epsilon;
            }

            private int child(float x, float y, float z) {
                for (int i = 0; i < children.length; i++) {
                    OctahedronTree c = children[i];
                    if (isPointOnSphericalTriangle(x, y, z, c.v0x, c.v0y, c.v0z, c.v1x, c.v1y, c.v1z, c.v2x, c.v2y, c.v2z, 1E-5f)) {
                        return i;
                    }
                }
                // No child found. This can happen in 'nearest()' when querying possible nearby nodes
                return 0;
            }

            private float greatCircleDist(float x1, float y1, float z1, float x2, float y2, float z2) {
                return Math.abs((float) Math.acos(x1 * x2 + y1 * y2 + z1 * z2));
            }

            float nearest(float x, float y, float z, float n) {
                float gcd = greatCircleDist(x, y, z, cx, cy, cz);
                if (gcd - arc > n)
                    return n;
                float nr = n;
                if (children != null) {
                    for (int i = child(x, y, z), c = 0; c < children.length; i = (i + 1) % children.length, c++) {
                        float n1 = children[i].nearest(x, y, z, nr);
                        nr = Math.min(n1, nr);
                    }
                    return nr;
                }
                for (int i = 0; objects != null && i < objects.size(); i++) {
                    Vector3f o = (Vector3f) objects.get(i);
                    float d = greatCircleDist(o.x, o.y, o.z, x, y, z);
                    if (d < nr) {
                        nr = d;
                    }
                }
                return nr;
            }
        }

        private final Random rnd;

        private final OctahedronTree otree;

        /**
         * Create a new instance of {@link BestCandidateSampling}, initialize the random number generator with the given <code>seed</code> and generate <code>numSamples</code>
         * number of 'best candidate' sample positions on the unit sphere with each sample tried <code>numCandidates</code> number of times, and call the given
         * <code>callback</code> for each sample generate.
         * 
         * @param seed
         *            the seed to initialize the random number generator with
         * @param numSamples
         *            the number of samples to generate
         * @param numCandidates
         *            the number of candidates to test for each sample
         * @param callback
         *            will be called for each sample generated
         */
        public Sphere(long seed, int numSamples, int numCandidates, Callback3d callback) {
            this.rnd = new Random(seed);
            this.otree = new OctahedronTree();
            compute(numSamples, numCandidates, callback);
        }

        private void compute(int numSamples, int numCandidates, Callback3d callback) {
            Vector3f tmp = new Vector3f();
            for (int i = 0; i < numSamples; i++) {
                float bestX = 0, bestY = 0, bestZ = 0, bestDist = 0.0f;
                for (int c = 0; c < numCandidates; c++) {
                    randomVectorOnSphere(tmp);
                    float minDist = otree.nearest(tmp.x, tmp.y, tmp.z, Float.POSITIVE_INFINITY);
                    if (minDist > bestDist) {
                        bestDist = minDist;
                        bestX = tmp.x;
                        bestY = tmp.y;
                        bestZ = tmp.z;
                    }
                    if (minDist == Float.POSITIVE_INFINITY)
                        break;
                }
                callback.onNewSample(bestX, bestY, bestZ);
                otree.insert(new Vector3f(bestX, bestY, bestZ));
            }
        }

        private void randomVectorOnSphere(Vector3f out) {
            float x1, x2;
            do {
                x1 = rnd.nextFloat() * 2.0f - 1.0f;
                x2 = rnd.nextFloat() * 2.0f - 1.0f;
            } while (x1 * x1 + x2 * x2 > 1.0f);
            float sqrt = (float) Math.sqrt(1.0 - x1 * x1 - x2 * x2);
            out.x = 2 * x1 * sqrt;
            out.y = 2 * x2 * sqrt;
            out.z = 1.0f - 2.0f * (x1 * x1 + x2 * x2);
        }
    }

    /**
     * Generates Best Candidate samples on a unit disk.
     * 
     * @author Kai Burjack
     */
    public static class Disk {
        /**
         * Simple quatree that can handle points and 1-nearest neighbor search.
         * 
         * @author Kai Burjack
         */
        private static class QuadTree {

            private static final int MAX_OBJECTS_PER_NODE = 32;

            // Constants for the quadrants of the quadtree
            private static final int PXNY = 0;
            private static final int NXNY = 1;
            private static final int NXPY = 2;
            private static final int PXPY = 3;

            private float minX, minY, hs;
            private ArrayList objects;
            private QuadTree[] children;

            QuadTree(float minX, float minY, float size) {
                this.minX = minX;
                this.minY = minY;
                this.hs = size * 0.5f;
            }

            private void split() {
                children = new QuadTree[4];
                children[NXNY] = new QuadTree(minX, minY, hs);
                children[PXNY] = new QuadTree(minX + hs, minY, hs);
                children[NXPY] = new QuadTree(minX, minY + hs, hs);
                children[PXPY] = new QuadTree(minX + hs, minY + hs, hs);
            }

            private void insertIntoChild(Vector2f o) {
                float xm = minX + hs;
                float ym = minY + hs;
                if (o.x >= xm) {
                    if (o.y >= ym) {
                        children[PXPY].insert(o);
                    } else {
                        children[PXNY].insert(o);
                    }
                } else {
                    if (o.y >= ym) {
                        children[NXPY].insert(o);
                    } else {
                        children[NXNY].insert(o);
                    }
                }
            }

            void insert(Vector2f object) {
                if (children != null) {
                    insertIntoChild(object);
                    return;
                }
                if (objects != null && objects.size() == MAX_OBJECTS_PER_NODE) {
                    split();
                    for (int i = 0; i < objects.size(); i++) {
                        insertIntoChild((Vector2f) objects.get(i));
                    }
                    objects = null;
                    insertIntoChild(object);
                } else {
                    if (objects == null)
                        objects = new ArrayList(32);
                    objects.add(object);
                }
            }

            private int quadrant(float x, float y) {
                if (x < minX + hs) {
                    if (y < minY + hs)
                        return NXNY;
                    return NXPY;
                }
                if (y < minY + hs)
                    return PXNY;
                return PXPY;
            }

            float nearest(float x, float y, float n) {
                float nr = n;
                if (x < minX - n || x > minX + hs * 2 + n || y < minY - n || y > minY + hs * 2 + n) {
                    return nr;
                }
                if (children != null) {
                    for (int i = quadrant(x, y), c = 0; c < 4; i = (i + 1) % 4, c++) {
                        float n1 = children[i].nearest(x, y, nr);
                        nr = Math.min(n1, nr);
                    }
                    return nr;
                }
                float nr2 = nr * nr;
                for (int i = 0; objects != null && i < objects.size(); i++) {
                    Vector2f o = (Vector2f) objects.get(i);
                    float d = o.distanceSquared(x, y);
                    if (d < nr2) {
                        nr2 = d;
                    }
                }
                return (float) Math.sqrt(nr2);
            }

            public float nearest(float x, float y) {
                return nearest(x, y, Float.POSITIVE_INFINITY);
            }
        }

        private final Random rnd;

        private final QuadTree qtree;

        /**
         * Create a new instance of {@link BestCandidateSampling}, initialize the random number generator with the given <code>seed</code> and generate <code>numSamples</code>
         * number of 'best candidate' sample positions on the unit disk with each sample tried <code>numCandidates</code> number of times, and call the given <code>callback</code>
         * for each sample generate.
         * 
         * @param seed
         *            the seed to initialize the random number generator with
         * @param numSamples
         *            the number of samples to generate
         * @param numCandidates
         *            the number of candidates to test for each sample
         * @param callback
         *            will be called for each sample generated
         */
        public Disk(long seed, int numSamples, int numCandidates, Callback2d callback) {
            this.rnd = new Random(seed);
            this.qtree = new QuadTree(-1, -1, 2);
            generate(numSamples, numCandidates, callback);
        }

        private void generate(int numSamples, int numCandidates, Callback2d callback) {
            Vector2f tmp = new Vector2f();
            for (int i = 0; i < numSamples; i++) {
                float bestX = 0, bestY = 0, bestDist = 0.0f;
                for (int c = 0; c < numCandidates; c++) {
                    randomVectorOnDisk(tmp);
                    float minDist = qtree.nearest(tmp.x, tmp.y);
                    if (minDist > bestDist) {
                        bestDist = minDist;
                        bestX = tmp.x;
                        bestY = tmp.y;
                    }
                }
                callback.onNewSample(bestX, bestY);
                qtree.insert(new Vector2f(bestX, bestY));
            }
        }

        private void randomVectorOnDisk(Vector2f out) {
            float x, y;
            do {
                x = rnd.nextFloat() * 2.0f - 1.0f;
                y = rnd.nextFloat() * 2.0f - 1.0f;
            } while (x * x + y * y > 1.0f);
            out.x = x;
            out.y = y;
        }
    }

}