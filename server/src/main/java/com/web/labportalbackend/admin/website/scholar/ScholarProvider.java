package com.web.labportalbackend.admin.website.scholar;

public interface ScholarProvider {
    ScholarSnapshot fetch(String authorId);
}
