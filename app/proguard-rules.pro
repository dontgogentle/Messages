# EventBus
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Gson
-keep class org.fossify.commons.models.SimpleContact { *; }
-keep class org.fossify.messages.models.Attachment { *; }
-keep class org.fossify.messages.models.MessageAttachment { *; }

    # Assuming your TransactionInfo class is at org.fossify.messages.models.TransactionInfo
    -keep class org.fossify.messages.models.TransactionInfo { *; }
    -keepnames class org.fossify.messages.models.TransactionInfo { *; }

    # More specific: keep all fields in TransactionInfo
    -keepclassmembers class org.fossify.messages.models.TransactionInfo {
        public <fields>;
        # If you have methods that Firebase might call (like getters/setters if not a data class)
        # public <methods>;
    }

    # If Firebase needs a no-argument constructor
    -keepclassmembers class org.fossify.messages.models.TransactionInfo {
        public <init>();
    }

# Keep the entire class and all its members
-keep class org.fossify.messages.models.GPayTransactionInfo { *; }
-keepnames class org.fossify.messages.models.GPayTransactionInfo { *; }

# Preserve all public fields (for Firebase, Gson, etc.)
-keepclassmembers class org.fossify.messages.models.GPayTransactionInfo {
    public <fields>;
}

# Preserve public methods (getters/setters, if applicable)
-keepclassmembers class org.fossify.messages.models.GPayTransactionInfo {
    public <methods>;
}

# Preserve no-argument constructor (required by Firebase)
-keepclassmembers class org.fossify.messages.models.GPayTransactionInfo {
    public <init>();
}
