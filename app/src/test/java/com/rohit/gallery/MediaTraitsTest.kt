package com.rohit.gallery

import com.rohit.gallery.core.data.mediastore.MediaTraits
import com.rohit.gallery.core.data.mediastore.traitsOf
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTraitsTest {

    @Test fun plainJpeg() {
        assertEquals(MediaTraits.None, traitsOf("\u00FF\u00D8\u00FF\u00E1 Exif MM some bytes"))
    }

    @Test fun ultraHdr() {
        val xmp = """<x:xmpmeta><rdf:Description hdrgm:Version="1.0" Item:Semantic="GainMap"/></x:xmpmeta>"""
        assertEquals(MediaTraits(ultraHdr = true), traitsOf("....$xmp...."))
    }

    @Test fun motionPhotoAttributeAndElementForms() {
        assertEquals(true, traitsOf("""<rdf:Description GCamera:MotionPhoto="1" GCamera:MotionPhotoVersion="1">""").motionPhoto)
        assertEquals(true, traitsOf("""<Camera:MotionPhoto>1</Camera:MotionPhoto>""").motionPhoto)
        assertEquals(true, traitsOf("""<rdf:Description GCamera:MicroVideo="1" GCamera:MicroVideoOffset="123">""").motionPhoto)
    }

    @Test fun motionPhotoOffIsNotMotion() {
        assertEquals(false, traitsOf("""<rdf:Description GCamera:MotionPhoto="0">""").motionPhoto)
    }
}
