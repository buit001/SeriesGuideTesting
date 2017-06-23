/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * This code was generated by https://github.com/google/apis-client-generator/
 * (build: 2017-02-15 17:18:02 UTC)
 * on 2017-06-23 at 19:14:47 UTC 
 * Modify at your own risk.
 */

package com.uwetrottmann.seriesguide.backend.movies.model;

/**
 * Model definition for MovieList.
 *
 * <p> This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the movies. For a detailed explanation see:
 * <a href="https://developers.google.com/api-client-library/java/google-http-java-client/json">https://developers.google.com/api-client-library/java/google-http-java-client/json</a>
 * </p>
 *
 * @author Google, Inc.
 */
@SuppressWarnings("javadoc")
public final class MovieList extends com.google.api.client.json.GenericJson {

  /**
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.lang.String cursor;

  /**
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.util.List<Movie> movies;

  static {
    // hack to force ProGuard to consider Movie used, since otherwise it would be stripped out
    // see https://github.com/google/google-api-java-client/issues/543
    com.google.api.client.util.Data.nullOf(Movie.class);
  }

  /**
   * @return value or {@code null} for none
   */
  public java.lang.String getCursor() {
    return cursor;
  }

  /**
   * @param cursor cursor or {@code null} for none
   */
  public MovieList setCursor(java.lang.String cursor) {
    this.cursor = cursor;
    return this;
  }

  /**
   * @return value or {@code null} for none
   */
  public java.util.List<Movie> getMovies() {
    return movies;
  }

  /**
   * @param movies movies or {@code null} for none
   */
  public MovieList setMovies(java.util.List<Movie> movies) {
    this.movies = movies;
    return this;
  }

  @Override
  public MovieList set(String fieldName, Object value) {
    return (MovieList) super.set(fieldName, value);
  }

  @Override
  public MovieList clone() {
    return (MovieList) super.clone();
  }

}
