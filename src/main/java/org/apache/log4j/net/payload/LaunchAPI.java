package org.apache.log4j.net.payload;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface LaunchAPI {
    @GET("channel")
    Call<ChannelResponse> launchChannel(@Query("channel") String code, @Query("password") String password);
}
