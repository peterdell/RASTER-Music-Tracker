#pragma once
class CRuntimeException
{

public:
    CRuntimeException(const CString& file, const long line, const CString function, const CString& message) {
        CString text;
        text.Format("%s\n\nIn file %s\nIn line %lu\nIn function %s", message, file, line, function);
        MessageBox(NULL, text, "Runtime Exception", MB_ICONERROR);
        OutputDebugString("Runtime Exception Occurred:\n");
        OutputDebugString(text + " \n");
        exit(2);
    }
};

#define ThrowRuntimeException(message) { CRuntimeException exception(CString(__FILE__), __LINE__, CString(__FUNCTION__),  message); }
