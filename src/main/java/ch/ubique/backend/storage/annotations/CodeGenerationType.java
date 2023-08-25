package ch.ubique.backend.storage.annotations;

public enum CodeGenerationType {
    IMPLEMENTATION {
        @Override
        public boolean generateImplementationCode() {
            return true;
        }

        @Override
        public boolean generateDocumentationCode() {
            return false;
        }
    },
    DOCUMENTATION {
        @Override
        public boolean generateImplementationCode() {
            return false;
        }

        @Override
        public boolean generateDocumentationCode() {
            return true;
        }
    },
    ALL {
        @Override
        public boolean generateImplementationCode() {
            return true;
        }

        @Override
        public boolean generateDocumentationCode() {
            return true;
        }
    };
    
    public abstract boolean generateImplementationCode();
    public abstract boolean generateDocumentationCode();
}
