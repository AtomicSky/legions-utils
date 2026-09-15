package com.legions.client.access;

public interface LegionsPlayerOverlayRenderStateAccess {
    void legions_client$setQuipOutline(int color, float expansion);
    int legions_client$getQuipOutlineColor();
    float legions_client$getQuipOutlineExpansion();
    boolean legions_client$markQuipOutlinePass(Object passKey);
    void legions_client$setFoeOverlayColor(int color);
    int legions_client$getFoeOverlayColor();
    void legions_client$setFoeOverlayStyle(int style);
    int legions_client$getFoeOverlayStyle();
}
