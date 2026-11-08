package com.blackout.gallery.settings

/** Icon drawings, in 108×108 adaptive-icon units. Generated together with the app drawer icons so both match. */
object IconShapes {
    class Shape(val stroke: Float, val onBackground: Boolean, val path: String)
    val STYLES: Map<String, Pair<Boolean, List<Shape>>> = mapOf(
        "A" to Pair(true, listOf(
            Shape(5.0f, false, "M34,36h40a4,4 0 0 1 4,4v28a4,4 0 0 1 -4,4h-40a4,4 0 0 1 -4,-4v-28a4,4 0 0 1 4,-4z"),
            Shape(0f, false, "M39,49a5,5 0 1 0 10,0a5,5 0 1 0 -10,0z"),
            Shape(0f, false, "M33,69L48,54L57,63L64,56L76,68L76,69z"),
        )),
        "B" to Pair(false, listOf(
            Shape(2.4f, false, "M54,22.5a31.5,31.5 0 1 1 0,63a31.5,31.5 0 1 1 0,-63z"),
            Shape(4.0f, false, "M38,40h32a3,3 0 0 1 3,3v22a3,3 0 0 1 -3,3h-32a3,3 0 0 1 -3,-3v-22a3,3 0 0 1 3,-3z"),
            Shape(0f, false, "M42,50a4,4 0 1 0 8,0a4,4 0 1 0 -8,0z"),
            Shape(0f, false, "M37,66L49,54L56,61L62,55L71,64L71,66z"),
        )),
        "C" to Pair(true, listOf(
            Shape(0f, false, "M32,40a8,8 0 1 0 16,0a8,8 0 1 0 -16,0z"),
            Shape(0f, false, "M22,80L46,56L59,69L69,59L86,76L86,80z"),
        )),
        "D" to Pair(true, listOf(
            Shape(4.0f, false, "M44,30h32a4,4 0 0 1 4,4v26a4,4 0 0 1 -4,4h-32a4,4 0 0 1 -4,-4v-26a4,4 0 0 1 4,-4z"),
            Shape(0f, true, "M28,40h44a4,4 0 0 1 4,4v34a4,4 0 0 1 -4,4h-44a4,4 0 0 1 -4,-4v-34a4,4 0 0 1 4,-4z"),
            Shape(0f, false, "M30,42h40a2,2 0 0 1 2,2v32a2,2 0 0 1 -2,2h-40a2,2 0 0 1 -2,-2v-32a2,2 0 0 1 2,-2z"),
            Shape(0f, true, "M35,52a5,5 0 1 0 10,0a5,5 0 1 0 -10,0z"),
            Shape(0f, true, "M30,76L44,62L52,70L58,64L70,76z"),
        )),
    )
    val NAMES = mapOf("A" to "Classic", "B" to "Ring", "C" to "Big view", "D" to "Stack")
}
